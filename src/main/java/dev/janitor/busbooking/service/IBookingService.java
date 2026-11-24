package dev.janitor.busbooking.service;

import java.util.List;

import dev.janitor.busbooking.consts.BookingStatus;
import dev.janitor.busbooking.consts.PaymentOutcome;
import dev.janitor.busbooking.model.Booking;

public interface IBookingService {
	// a fresh hold plus the token the gateway pay page is opened with
	record PaymentHold(Booking booking, String paymentToken) {}

	// claims the seats as PAYMENT_HOLDING, paid later through the gateway. one hold per user
	// ASSIGNED_SEATING: seats (names) required, quantity ignored. OPEN_SEATING: quantity required, seats ignored
	// passengerName / passengerPhone null = the account holder's
	PaymentHold hold(long userId, long tripId, String passengerName, String passengerPhone, Integer quantity, List<String> seats);

	// the phase 1 one-shot (?legacy): claims the seats as CONFIRMED right away, no hold, no payment
	Booking bookLegacy(long userId, long tripId, String passengerName, String passengerPhone, Integer quantity, List<String> seats);

	// for the gateway pay page: the booking behind a live token, still waiting for payment
	Booking getForPayment(String paymentToken);

	// the gateway's (signed) verdict. APPROVED confirms; a late one abandons the hold and refunds
	// DECLINED / TIMED_OUT abandon the hold. safe to call twice
	Booking settlePayment(long bookingId, PaymentOutcome outcome);

	// owner or admin only
	Booking get(long userId, boolean admin, long bookingId);

	List<Booking> history(long userId);

	// null filters are ignored
	List<Booking> adminSearch(Long userId, Long tripId, BookingStatus status);

	// owner or admin, before the trip's booking cutoff only
	// a hold is abandoned (seats freed now), a paid booking is cancelled with a full refund
	Booking cancel(long userId, boolean admin, long bookingId);

	// the hold gc: abandons up to one batch of expired holds, returns how many
	int sweepExpiredHolds();
}
