package dev.janitor.busbooking.impl.cache;

import java.util.List;
import java.util.Optional;
import java.util.function.LongSupplier;

import dev.janitor.busbooking.consts.TripStatus;
import dev.janitor.busbooking.model.Trip;
import dev.janitor.busbooking.repository.ITripRepository;

// forwards everything; a successful admin edit or cancel files a premium ticket (deleted right now)
public class CacheTicketingTripRepository implements ITripRepository {
	private final ITripRepository trips;
	private final TripCacheInvalidationQueue invalidationQueue;
	
	public CacheTicketingTripRepository(ITripRepository trips, TripCacheInvalidationQueue invalidationQueue) {
		this.trips = trips;
		this.invalidationQueue = invalidationQueue;
	}
	
	@Override
	public Optional<Trip> get(long tripId) {
		return trips.get(tripId);
	}
	
	@Override
	public List<Trip> search(Long originId, Long destinationId, Long departureFrom, Long departureTo) {
		return trips.search(originId, destinationId, departureFrom, departureTo);
	}
	
	@Override
	public List<Trip> adminSearch(Long routeId, TripStatus status, Long departureFrom, Long departureTo) {
		return trips.adminSearch(routeId, status, departureFrom, departureTo);
	}
	
	@Override
	public Trip create(Trip trip) {
		return trips.create(trip); // nothing cached for a trip that did not exist
	}
	
	@Override
	public boolean update(Trip trip, long now) {
		boolean updated = trips.update(trip, now);
		if (updated) {
			invalidationQueue.premium(trip.id());
		}
		return updated;
	}
	
	@Override
	public boolean cancel(long tripId, long cancelledAt, LongSupplier refundIds) {
		boolean cancelled = trips.cancel(tripId, cancelledAt, refundIds);
		if (cancelled) {
			invalidationQueue.premium(tripId);
		}
		return cancelled;
	}
	
	@Override
	public boolean hasActiveBookings(long tripId) {
		return trips.hasActiveBookings(tripId);
	}
}
