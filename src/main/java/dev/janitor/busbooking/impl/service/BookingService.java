package dev.janitor.busbooking.impl.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.janitor.busbooking.bootstrap.ApplicationConfig;
import dev.janitor.busbooking.consts.BookingStatus;
import dev.janitor.busbooking.consts.PaymentOutcome;
import dev.janitor.busbooking.consts.ReserveResult;
import dev.janitor.busbooking.consts.SeatsType;
import dev.janitor.busbooking.consts.ServiceFailure;
import dev.janitor.busbooking.consts.TripStatus;
import dev.janitor.busbooking.model.Booking;
import dev.janitor.busbooking.model.SeatSet;
import dev.janitor.busbooking.model.Trip;
import dev.janitor.busbooking.model.User;
import dev.janitor.busbooking.repository.IBookingRepository;
import dev.janitor.busbooking.repository.IPaymentProcessor;
import dev.janitor.busbooking.repository.IPaymentTokenStore;
import dev.janitor.busbooking.repository.IRefundRepository;
import dev.janitor.busbooking.repository.ITripRepository;
import dev.janitor.busbooking.repository.IUserRepository;
import dev.janitor.busbooking.service.IBookingService;
import dev.janitor.busbooking.service.ServiceException;
import dev.janitor.busbooking.utils.Snowflake;
import dev.janitor.busbooking.utils.Sputnik;

// a booking is born PAYMENT_HOLDING, then either CONFIRMED (paid) or ABANDONED_PAYMENT (expired, declined, dropped)
// a CONFIRMED one can still become CANCELLED (full refund) before the trip's booking cutoff
// every status change is a guarded UPDATE in the repo, so whoever flips it first wins and the rest see false
public class BookingService implements IBookingService {
	private static final int MAX_PASSENGER_NAME_LENGTH = 100;
	private static final long NO_REFUND = 0; // release() only reads the refund id for CONFIRMED -> CANCELLED

	private final IBookingRepository bookingRepo;
	private final ITripRepository tripRepo;
	private final IUserRepository userRepo;
	private final IRefundRepository refundRepo;
	private final IPaymentTokenStore paymentTokens;
	private final IPaymentProcessor paymentProcessor;
	private final int maxSeatsPerBooking;
	private final long paymentExpiryMs;
	private final int holdSweepBatchSize;

	public BookingService(
		IBookingRepository bookingRepo, ITripRepository tripRepo, IUserRepository userRepo,
		IRefundRepository refundRepo, IPaymentTokenStore paymentTokens, IPaymentProcessor paymentProcessor,
		ApplicationConfig config
	) {
		this.bookingRepo = bookingRepo;
		this.tripRepo = tripRepo;
		this.userRepo = userRepo;
		this.refundRepo = refundRepo;
		this.paymentTokens = paymentTokens;
		this.paymentProcessor = paymentProcessor;
		this.maxSeatsPerBooking = config.getMaxSeatsPerBooking();
		this.paymentExpiryMs = config.getPaymentHoldTtlSeconds() * 1000L;
		this.holdSweepBatchSize = config.getHoldSweepBatchSize();
	}

	@Override
	public PaymentHold hold(long userId, long tripId, String passengerName, String passengerPhone, Integer quantity, List<String> seats) {
		Booking booking = this.reserveHold(userId, tripId, passengerName, passengerPhone, quantity, seats);
		String paymentToken = UUID.randomUUID().toString();
		// the token dies with the hold, a pay page opened after that is just a dead link
		paymentTokens.put(paymentToken, booking.id(), this.paymentExpiryMs);
		return new PaymentHold(booking, paymentToken);
	}

	@Override
	public Booking holdAndPay(long userId, long tripId, String passengerName, String passengerPhone, Integer quantity, List<String> seats) {
		Booking held = this.reserveHold(userId, tripId, passengerName, passengerPhone, quantity, seats);
		PaymentOutcome outcome = paymentProcessor.charge(held.id(), held.totalAmount());
		Booking settled = this.settlePayment(held.id(), outcome);
		if (settled.status() != BookingStatus.CONFIRMED) {
			throw ServiceException.conflict("Payment " + outcome + ", the seats were released");
		}
		return settled;
	}
	
	@Override
	public Booking getForPayment(String paymentToken) {
		String token = Sputnik.trimToNull(paymentToken);
		if (token == null) {
			throw ServiceException.notFound("Payment link expired or unknown");
		}
		var bookingId = paymentTokens.getBookingId(token);
		if (bookingId.isEmpty()) {
			throw ServiceException.notFound("Payment link expired or unknown");
		}
		Booking booking = this.requireBooking(bookingId.get());
		// the token can outlive the hold by a hair, or the user dropped the hold already
		boolean expired = System.currentTimeMillis() - booking.paymentStartTime() >= this.paymentExpiryMs;
		if (booking.status() != BookingStatus.PAYMENT_HOLDING || expired) {
			throw ServiceException.conflict("This booking is not waiting for payment anymore");
		}
		return booking;
	}
	
	@Override
	public Booking settlePayment(long bookingId, PaymentOutcome outcome) {
		this.requireBooking(bookingId);
		long now = System.currentTimeMillis();
		if (outcome != PaymentOutcome.APPROVED) {
			// declined or timed out; nothing was paid, the seats go back right away
			bookingRepo.release(bookingId, BookingStatus.PAYMENT_HOLDING, BookingStatus.ABANDONED_PAYMENT, now, NO_REFUND);
			return this.requireBooking(bookingId);
		}
		if (bookingRepo.confirm(bookingId, now, this.paymentExpiryMs)) {
			return this.requireBooking(bookingId);
		}
		Booking latest = this.requireBooking(bookingId);
		if (latest.status() == BookingStatus.CONFIRMED || latest.status() == BookingStatus.CANCELLED) {
			return latest; // the gateway told us twice, the first one already did everything
		}
		// late: still holding but expired (surgical gc, the sweeper has not come by yet), or already abandoned
		// either way the money moved for seats nobody is getting, so it goes back
		if (latest.status() == BookingStatus.PAYMENT_HOLDING) {
			bookingRepo.release(bookingId, BookingStatus.PAYMENT_HOLDING, BookingStatus.ABANDONED_PAYMENT, now, NO_REFUND);
		}
		refundRepo.refundLatePayment(Snowflake.get().nextId(), bookingId, now);
		return this.requireBooking(bookingId);
	}
	
	@Override
	public Booking get(long userId, boolean admin, long bookingId) {
		Booking booking = this.requireBooking(bookingId);
		requireAccess(userId, admin, booking);
		return booking;
	}
	
	@Override
	public List<Booking> history(long userId) {
		return bookingRepo.listByUser(userId);
	}
	
	@Override
	public List<Booking> adminSearch(Long userId, Long tripId, BookingStatus status) {
		return bookingRepo.adminSearch(userId, tripId, status);
	}
	
	@Override
	public Booking cancel(long userId, boolean admin, long bookingId) {
		Booking booking = this.requireBooking(bookingId);
		requireAccess(userId, admin, booking);
		long now = System.currentTimeMillis();
		Trip trip = this.requireTrip(booking.tripId());
		requireBeforeCutoff(trip, now);
		
		boolean released;
		if (booking.status() == BookingStatus.PAYMENT_HOLDING) {
			// freed right now, the user can hold again immediately
			released = bookingRepo.release(bookingId, 
				BookingStatus.PAYMENT_HOLDING, BookingStatus.ABANDONED_PAYMENT, 
				now, NO_REFUND
			);
		} else if (booking.status() == BookingStatus.CONFIRMED) {
			released = bookingRepo.release(bookingId, 
				BookingStatus.CONFIRMED, BookingStatus.CANCELLED,
				now, Snowflake.get().nextId()
			);
		} else {
			throw ServiceException.conflict("Booking is already " + booking.status());
		}
		if (!released) {
			// paid, expired or cancelled by someone else between our read and the flip
			throw ServiceException.conflict("Booking changed meanwhile, it is now " + this.requireBooking(bookingId).status());
		}
		return this.requireBooking(bookingId);
	}

	@Override
	public int sweepExpiredHolds() {
		long now = System.currentTimeMillis();
		int abandonedHolds = 0;
		for (Booking expired : bookingRepo.findExpiredHolds(now, this.paymentExpiryMs, this.holdSweepBatchSize)) {
			// false = paid or dropped in the meantime, not ours anymore
			if (bookingRepo.release(expired.id(), BookingStatus.PAYMENT_HOLDING, BookingStatus.ABANDONED_PAYMENT, now, NO_REFUND)) {
				++abandonedHolds;
			}
		}
		return abandonedHolds;
	}
	
	// reserve but not paid yet; all fate on the payment processor
	private Booking reserveHold(
		long userId, long tripId, String passengerName, String passengerPhone, Integer quantity, List<String> seats
	) {
		User user = this.requireUser(userId);
		// no name/phone = booking for yourself
		String name = Sputnik.trimToNull(passengerName);
		if (name == null) {
			name = user.fullName();
		}
		name = Sputnik.requireText(name, "passengerName", MAX_PASSENGER_NAME_LENGTH);
		String phone = Sputnik.trimToNull(passengerPhone);
		if (phone == null) {
			phone = user.phone();
		}
		phone = Sputnik.requirePhone(phone, "passengerPhone");

		long now = System.currentTimeMillis();
		Trip trip = this.requireTrip(tripId);
		if (trip.status() != TripStatus.SCHEDULED) {
			throw ServiceException.conflict("Trip is " + trip.status() + ", not open for booking");
		}
		requireBeforeCutoff(trip, now);

		SeatSet desiredSeats = SeatSet.EMPTY;
		int desiredOpenSeats = 0;
		int seatCount;
		// for assigned set, convert a firendly list into a bitset128
		if (trip.seatsType() == SeatsType.ASSIGNED_SEATING) {
			desiredSeats = this.toSeatSet(trip, seats);
			seatCount = desiredSeats.popcount();
		} else {
			desiredOpenSeats = Sputnik.requireRange(quantity, "quantity", 1, this.maxSeatsPerBooking);
			seatCount = desiredOpenSeats;
		}
		Booking booking = new Booking(Snowflake.get().nextId(),
			"BK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20),
			userId, tripId,
			name, phone,
			trip.price(),
			desiredSeats,desiredOpenSeats,
			trip.price() * seatCount,
			BookingStatus.PAYMENT_HOLDING,
			now, 
			0, 0 // not booked and not invalidated yet
		);
		ReserveResult result = bookingRepo.reserve(booking, trip.seatsType());
		if (result == ReserveResult.ALREADY_HOLDING) {
			// prevent bot users from hoarding all the tickets, the user is already waiting on payment
			// dont let them buy more
			throw ServiceException.conflict("You already have a booking waiting for payment, pay or cancel it first");
		}
		if (result == ReserveResult.SEATS_UNAVAILABLE) {
			if (trip.seatsType() == SeatsType.ASSIGNED_SEATING) {
				throw ServiceException.conflict("Some of these seats are already taken");
			}
			throw ServiceException.conflict("Not enough seats left");
		}
		return booking;
	}
	
	// names -> bits of the trip's seat map. trimmed, case insensitive, every name must exist, no repeats
	// the names are whatever the operator called them ("TH-36"), so no pattern checks, only the map
	private SeatSet toSeatSet(Trip trip, List<String> seats) {
		if (seats == null || seats.isEmpty()) {
			throw ServiceException.invalid("seats is required for this trip");
		}
		if (seats.size() > this.maxSeatsPerBooking) {
			throw ServiceException.invalid("At most " + this.maxSeatsPerBooking + " seats per booking");
		}
		Map<String, Integer> seatIndexes = new HashMap<>();
		List<String> seatNames = trip.assignedSeatNames();
		for (int i = 0; i < seatNames.size(); ++i) {
			seatIndexes.put(Sputnik.toLower(seatNames.get(i)), i);
		}
		int desiredIndexes[] = new int[seats.size()];
		SeatSet seenSeats = SeatSet.EMPTY;
		for (int i = 0; i < seats.size(); ++i) {
			String seatName = Sputnik.requireText(seats.get(i), "seat", 32);
			Integer seatIndex = seatIndexes.get(Sputnik.toLower(seatName));
			if (seatIndex == null) {
				throw ServiceException.invalid("No seat named " + seatName + " on this trip");
			}
			if (seenSeats.get(seatIndex)) {
				throw ServiceException.invalid("Seat " + seatName + " is listed twice");
			}
			seenSeats = seenSeats.or(SeatSet.of(seatIndex));
			desiredIndexes[i] = seatIndex;
		}
		return SeatSet.of(desiredIndexes);
	}
	
	// after departure, cutoff nothing moves anymore, except paying a hold you already have
	private static void requireBeforeCutoff(Trip trip, long now) {
		if (now >= trip.departureTime() - trip.bookingCutoffMinutes() * 60_000L) {
			throw ServiceException.conflict("Bookings for this trip closed " + trip.bookingCutoffMinutes() + " minutes before departure");
		}
	}
	
	private static void requireAccess(long userId, boolean admin, Booking booking) {
		if (!admin && booking.userId() != userId) {
			throw new ServiceException(ServiceFailure.FORBIDDEN, "You do not have access to this booking");
		}
	}
	
	private Booking requireBooking(long bookingId) {
		var booking = bookingRepo.get(bookingId);
		if (booking.isEmpty()) {
			throw ServiceException.notFound("Booking not found");
		}
		return booking.get();
	}
	
	private Trip requireTrip(long tripId) {
		var trip = tripRepo.get(tripId);
		if (trip.isEmpty()) {
			throw ServiceException.notFound("Trip not found");
		}
		return trip.get();
	}
	
	private User requireUser(long userId) {
		var user = userRepo.get(userId);
		if (user.isEmpty()) {
			throw ServiceException.notFound("User not found");
		}
		return user.get();
	}
}
