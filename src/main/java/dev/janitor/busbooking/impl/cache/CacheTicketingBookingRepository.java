package dev.janitor.busbooking.impl.cache;

import java.util.List;
import java.util.Optional;

import dev.janitor.busbooking.consts.BookingStatus;
import dev.janitor.busbooking.consts.SeatsType;
import dev.janitor.busbooking.model.Booking;
import dev.janitor.busbooking.repository.IBookingRepository;

// forwards everything; a successful seat change files an economy ticket for its trip
// confirm does not move seats, so it files nothing
public class CacheTicketingBookingRepository implements IBookingRepository {
	private final IBookingRepository bookings;
	private final TripCacheInvalidationQueue invalidationQueue;
	
	public CacheTicketingBookingRepository(IBookingRepository bookings, TripCacheInvalidationQueue invalidationQueue) {
		this.bookings = bookings;
		this.invalidationQueue = invalidationQueue;
	}
	
	@Override
	public Optional<Booking> get(long bookingId) {
		return bookings.get(bookingId);
	}
	
	@Override
	public List<Booking> listByUser(long userId) {
		return bookings.listByUser(userId);
	}
	
	@Override
	public List<Booking> adminSearch(Long userId, Long tripId, BookingStatus status) {
		return bookings.adminSearch(userId, tripId, status);
	}
	
	@Override
	public boolean reserve(Booking booking, SeatsType seatsType) {
		boolean reserved = bookings.reserve(booking, seatsType);
		if (reserved) {
			invalidationQueue.economy(booking.tripId());
		}
		return reserved;
	}
	
	@Override
	public boolean confirm(long bookingId, long holdCutoff, long bookedAt) {
		return bookings.confirm(bookingId, holdCutoff, bookedAt);
	}
	
	@Override
	public boolean release(long bookingId, BookingStatus fromStatus, BookingStatus toStatus, long invalidatedAt, long refundId) {
		boolean released = bookings.release(bookingId, fromStatus, toStatus, invalidatedAt, refundId);
		if (released) {
			// trip id never changes after creation, a plain read is fine
			Optional<Booking> booking = bookings.get(bookingId);
			if (booking.isPresent()) {
				invalidationQueue.economy(booking.get().tripId());
			}
		}
		return released;
	}
	
	@Override
	public List<Booking> findExpiredHolds(long cutoff, int limit) {
		return bookings.findExpiredHolds(cutoff, limit);
	}
}
