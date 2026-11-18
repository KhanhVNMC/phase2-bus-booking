package dev.janitor.busbooking.impl.jpa.repos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.janitor.busbooking.bootstrap.HibernateRuntime;
import dev.janitor.busbooking.consts.BookingStatus;
import dev.janitor.busbooking.consts.SeatsType;
import dev.janitor.busbooking.impl.jpa.entities.BookingEntity;
import dev.janitor.busbooking.model.Booking;
import dev.janitor.busbooking.repository.IBookingRepository;

public class JPABookingRepository implements IBookingRepository {
	private final HibernateRuntime runtime;
	
	public JPABookingRepository(HibernateRuntime runtime) {
		this.runtime = runtime;
	}
	
	@Override
	public Optional<Booking> get(long bookingId) {
		try (var man = runtime.openEntityManager()) {
			BookingEntity entity = man.find(BookingEntity.class, bookingId);
			return entity == null ? Optional.empty() : Optional.of(entity.toModel());
		}
	}
	
	@Override
	public List<Booking> listByUser(long userId) {
		try (var man = runtime.openEntityManager()) {
			var query = man.createQuery(
				"SELECT b FROM BookingEntity b WHERE b.userId = :user_id ORDER BY b.id DESC",
				BookingEntity.class
			);
			query.setParameter("user_id", userId);
			return toModels(query.getResultList());
		}
	}
	
	@Override
	public List<Booking> adminSearch(Long userId, Long tripId, BookingStatus status) {
		String jpql = "SELECT b FROM BookingEntity b WHERE 1 = 1";
		if (userId != null) jpql += " AND b.userId = :user_id";
		if (tripId != null) jpql += " AND b.tripId = :trip_id";
		if (status != null)jpql += " AND b.status = :status";
		jpql += " ORDER BY b.id DESC";
		
		try (var man = runtime.openEntityManager()) {
			var query = man.createQuery(jpql, BookingEntity.class);
			if (userId != null) query.setParameter("user_id", userId);
			if (tripId != null) query.setParameter("trip_id", tripId);
			if (status != null) query.setParameter("status", status);
			return toModels(query.getResultList());
		}
	}
	
	@Override
	public boolean reserve(Booking booking, SeatsType seatsType) {
		return runtime.transact(man -> {
			int updatedRows;
			if (seatsType == SeatsType.ASSIGNED_SEATING) {
				// every assiggned seating; the desired mask must not be occupied
				// basically, a "seat map" is just a foking bitset128 (2 longs)
				// set = reserved, clear = free, basically, all the desired seats must be free:
				// if (current & desired == 0) { OK! }
				var claim = man.createNativeQuery("""
					UPDATE trips SET
						assigned_seating_booked_lo = assigned_seating_booked_lo | :seats_lo,
						assigned_seating_booked_hi = assigned_seating_booked_hi | :seats_hi
					WHERE trip_id = :trip_id
						AND status = 'SCHEDULED'
						AND seats_type = 'ASSIGNED_SEATING'
						AND (assigned_seating_booked_lo & :seats_lo) = 0
						AND (assigned_seating_booked_hi & :seats_hi) = 0
				""");
				claim.setParameter("seats_lo", booking.desiredAssignedSeats().lo());
				claim.setParameter("seats_hi", booking.desiredAssignedSeats().hi());
				claim.setParameter("trip_id", booking.tripId());
				updatedRows = claim.executeUpdate();
			} else {
				// for open seating (chicken pen/free range), just check the count
				var claim = man.createNativeQuery("""
					UPDATE trips SET
						open_seating_booked = open_seating_booked + :seat_count
					WHERE trip_id = :trip_id
						AND status = 'SCHEDULED'
						AND seats_type = 'OPEN_SEATING'
						AND open_seating_booked + :seat_count <= total_seats
				""");
				claim.setParameter("trip_id", booking.tripId());
				claim.setParameter("seat_count", booking.desiredOpenSeatsCount());
				updatedRows = claim.executeUpdate();
			}
			if (updatedRows != 1) {
				return false; // probably gone already
			}
			// if this insert throws, transact rolls the seat claim back with it
			man.persist(BookingEntity.fromModel(booking));
			return true;
		});
	}
	
	// basically this flips to CONFIRMED only fi the user current status is paying
	@Override
	public boolean confirm(long bookingId, long holdCutoff, long bookedAt) {
		return runtime.transact(man -> {
			var update = man.createQuery("""
				UPDATE BookingEntity b SET b.status = :confirmed, b.bookedAt = :booked_at
				WHERE b.id = :booking_id AND b.status = :holding AND b.paymentStartTime >= :hold_cutoff
			""");
			update.setParameter("confirmed", BookingStatus.CONFIRMED);
			update.setParameter("booked_at", bookedAt);
			update.setParameter("booking_id", bookingId);
			update.setParameter("holding", BookingStatus.PAYMENT_HOLDING);
			update.setParameter("hold_cutoff", holdCutoff);
			int updatedRows = update.executeUpdate();
			return updatedRows == 1;
		});
	}
	
	// either the user cancelled their ticket/booking or they abandoned it mid way (skemmer)
	@Override
	public boolean release(long bookingId, BookingStatus fromStatus, BookingStatus toStatus, long invalidatedAt) {
		boolean abandoningHold = fromStatus == BookingStatus.PAYMENT_HOLDING && toStatus == BookingStatus.ABANDONED_PAYMENT;
		boolean cancellingPaid = fromStatus == BookingStatus.CONFIRMED && toStatus == BookingStatus.CANCELLED;
		if (!abandoningHold && !cancellingPaid) {
			throw new IllegalArgumentException("Release cannot go from " + fromStatus + " to " + toStatus);
		}
		
		return runtime.transact(man -> {
			// the status flip is the gate: n concurrent releasers exactly one sees 1 row
			var statusFlip = man.createQuery("""
				UPDATE BookingEntity b SET b.status = :to_status, b.invalidatedAt = :invalidated_at
				WHERE b.id = :booking_id AND b.status = :from_status
			""");
			statusFlip.setParameter("to_status", toStatus);
			statusFlip.setParameter("from_status", fromStatus);
			statusFlip.setParameter("invalidated_at", invalidatedAt);
			statusFlip.setParameter("booking_id", bookingId);
			int flippedRows = statusFlip.executeUpdate();
			if (flippedRows != 1) {
				return false;
			}
			
			// trip id, seats and seats type never change after creation, so a plain read is fine here
			Booking booking = man.find(BookingEntity.class, bookingId).toModel();
			var typeQuery = man.createNativeQuery("SELECT seats_type FROM trips WHERE trip_id = :trip_id");
			typeQuery.setParameter("trip_id", booking.tripId());
			SeatsType seatsType = SeatsType.valueOf((String) typeQuery.getSingleResult());
			
			int releasedRows;
			if (seatsType == SeatsType.ASSIGNED_SEATING) {
				// every seat of this booking must still be booked (because THIS is holding it)
				// if yes then just flip them to zero to "free em up for the next victim"
				var release = man.createNativeQuery("""
					UPDATE trips SET
						assigned_seating_booked_lo = assigned_seating_booked_lo & ~(:seats_lo),
						assigned_seating_booked_hi = assigned_seating_booked_hi & ~(:seats_hi)
					WHERE trip_id = :trip_id
						AND assigned_seating_booked_lo & :seats_lo = :seats_lo
						AND assigned_seating_booked_hi & :seats_hi = :seats_hi
				""");
				release.setParameter("trip_id", booking.tripId());
				release.setParameter("seats_lo", booking.desiredAssignedSeats().lo());
				release.setParameter("seats_hi", booking.desiredAssignedSeats().hi());
				releasedRows = release.executeUpdate();
			} else {
				// a subtraction, thats it
				var release = man.createNativeQuery("""
					UPDATE trips SET
						open_seating_booked = open_seating_booked - :seat_count
					WHERE trip_id = :trip_id
					  AND open_seating_booked >= :seat_count
				""");
				release.setParameter("trip_id", booking.tripId());
				release.setParameter("seat_count", booking.desiredOpenSeatsCount());
				releasedRows = release.executeUpdate();
			}
			boolean seatsReleased = releasedRows == 1;
			if (!seatsReleased) {
				// the booking held seats the trip does not have
				throw new IllegalStateException("Booked seats out of sync for booking " + bookingId);
			}
			return true;
		});
	}
	
	// basically as the user didnt pay for a seat, after a point it turns into a DoS attack
	// this cleans it up
	@Override
	public List<Booking> findExpiredHolds(long cutoff, int limit) {
		try (var man = runtime.openEntityManager()) {
			var query = man.createQuery("""
				SELECT b FROM BookingEntity b
				WHERE b.status = :holding AND b.paymentStartTime < :cutoff
				ORDER BY b.paymentStartTime
			""", BookingEntity.class);
			query.setParameter("holding", BookingStatus.PAYMENT_HOLDING);
			query.setParameter("cutoff", cutoff);
			query.setMaxResults(limit);
			return toModels(query.getResultList());
		}
	}
	
	private static List<Booking> toModels(List<BookingEntity> entities) {
		List<Booking> bookings = new ArrayList<>();
		for (BookingEntity entity : entities) {
			bookings.add(entity.toModel());
		}
		return bookings;
	}
}
