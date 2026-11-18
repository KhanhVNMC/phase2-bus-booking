package dev.janitor.busbooking.repository;

import java.util.List;
import java.util.Optional;

import dev.janitor.busbooking.consts.TripStatus;
import dev.janitor.busbooking.model.Trip;

public interface ITripRepository {
	Optional<Trip> get(long tripId);
	
	// public search, ordered by departure. null filters are ignored, the window is [from, to)
	List<Trip> search(Long originId, Long destinationId, Long departureFrom, Long departureTo);
	
	List<Trip> adminSearch(Long routeId, TripStatus status, Long departureFrom, Long departureTo);
	
	Trip create(Trip trip);
	
	// never touches the booked state or the frozen seat names. false if the new total_seats
	// would go below what is already booked (open seating), or the trip is gone
	boolean update(Trip trip);
	
	// any PAYMENT_HOLDING or CONFIRMED booking on it
	boolean hasActiveBookings(long tripId);
}
