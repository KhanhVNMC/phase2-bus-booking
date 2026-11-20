package dev.janitor.busbooking.repository;

import java.util.List;
import java.util.function.LongSupplier;
import java.util.Optional;

import dev.janitor.busbooking.consts.TripStatus;
import dev.janitor.busbooking.model.Trip;

public interface ITripRepository {
	Optional<Trip> get(long tripId);
	
	List<Trip> adminSearch(Long routeId, TripStatus status, Long departureFrom, Long departureTo);
	
	Trip create(Trip trip);
	
	// route, vehicle, times, price and total seats; ASSIGNED_SEATING also replaces the seat names (vehicle swap)
	// never touches status, seats type, cutoff or the booked state
	// false = trip gone, not SCHEDULED, already departed (departure_time <= now),
	// or the new seats cannot hold the old ones (OPEN: booked > new total, ASSIGNED: new total < old total)
	boolean update(Trip trip, long now);
	
	// SCHEDULED | IN_PROGRESS -> CANCELLED, holds -> ABANDONED_PAYMENT, paid -> CANCELLED + a full refund each
	// all or nothing. false = trip gone or already COMPLETED / CANCELLED
	boolean cancel(long tripId, long cancelledAt, LongSupplier refundIds);
	
	// any PAYMENT_HOLDING or CONFIRMED booking on it
	boolean hasActiveBookings(long tripId);
}
