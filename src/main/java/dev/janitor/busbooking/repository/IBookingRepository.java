package dev.janitor.busbooking.repository;

import java.util.List;
import java.util.Optional;

import dev.janitor.busbooking.consts.BookingStatus;
import dev.janitor.busbooking.consts.SeatsType;
import dev.janitor.busbooking.model.Booking;

public interface IBookingRepository {
	Optional<Booking> get(long bookingId);
	
	List<Booking> listByUser(long userId);
	
	// null filters are ignored
	List<Booking> adminSearch(Long userId, Long tripId, BookingStatus status);
	
	// claims the seats on the trip and inserts the booking (PAYMENT_HOLDING), all or nothing
	// false = trip not SCHEDULED, wrong seats type, or the seats are not there. nothing is written then
	boolean reserve(Booking booking, SeatsType seatsType);
	
	// PAYMENT_HOLDING -> CONFIRMED, only if still holding and the hold started after holdCutoff
	boolean confirm(long bookingId, long holdCutoff, long bookedAt);
	
	// fromStatus -> toStatus (ABANDONED_PAYMENT | CANCELLED) and gives the seats back, all or nothing
	// CONFIRMED -> CANCELLED also queues a full refund with refundId (ignored otherwise)
	// false = the booking was not in fromStatus anymore (someone else got there first)
	boolean release(long bookingId, BookingStatus fromStatus, BookingStatus toStatus, long invalidatedAt, long refundId);
	
	// PAYMENT_HOLDING with paymentStartTime < cutoff, oldest -> newest
	List<Booking> findExpiredHolds(long cutoff, int limit);
}
