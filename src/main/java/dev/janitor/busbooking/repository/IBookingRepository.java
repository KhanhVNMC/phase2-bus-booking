package dev.janitor.busbooking.repository;

import java.util.List;
import java.util.Optional;

import dev.janitor.busbooking.consts.BookingStatus;
import dev.janitor.busbooking.consts.ReserveResult;
import dev.janitor.busbooking.consts.SeatsType;
import dev.janitor.busbooking.model.Booking;

public interface IBookingRepository {
	Optional<Booking> get(long bookingId);
	
	List<Booking> listByUser(long userId);
	
	// null filters are ignored
	List<Booking> adminSearch(Long userId, Long tripId, BookingStatus status);
	
	// claims the seats on the trip and inserts the booking (PAYMENT_HOLDING), all or nothing
	// anything but RESERVED writes nothing
	ReserveResult reserve(Booking booking, SeatsType seatsType);
	
	// a hold is expired once now - paymentStartTime >= paymentExpiryMs
	// PAYMENT_HOLDING -> CONFIRMED (bookedAt = now), only if still holding and not expired
	boolean confirm(long bookingId, long now, long paymentExpiryMs);
	
	// fromStatus -> toStatus (ABANDONED_PAYMENT | CANCELLED) and gives the seats back, all or nothing
	// CONFIRMED -> CANCELLED also queues a full refund with refundId (ignored otherwise)
	// false = the booking was not in fromStatus anymore (someone else got there first)
	boolean release(long bookingId, BookingStatus fromStatus, BookingStatus toStatus, long invalidatedAt, long refundId);
	
	// PAYMENT_HOLDING but expired (now - paymentStartTime >= paymentExpiryMs), oldest -> newest
	List<Booking> findExpiredHolds(long now, long paymentExpiryMs, int limit);
}
