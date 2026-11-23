package dev.janitor.busbooking.service;

import java.util.List;

import dev.janitor.busbooking.consts.SeatsType;
import dev.janitor.busbooking.consts.TripStatus;
import dev.janitor.busbooking.model.Trip;
import dev.janitor.busbooking.model.TripPublicView;

// all times are epoch millis, the edge converts
public interface ITripService {
	// public side, may be served from the cache
	TripPublicView getPublic(long tripId);

	// null filters are ignored, the departure window is [from, to)
	List<TripPublicView> searchPublic(Long originId, Long destinationId, Long departureFrom, Long departureTo);

    List<TripPublicView> getPublicMany(List<Long> tripIds);

	// admin side, always the db
	Trip get(long tripId);

	List<Trip> adminSearch(Long routeId, TripStatus status, Long departureFrom, Long departureTo);

	// starts SCHEDULED on an ACTIVE route with an ACTIVE vehicle
	// OPEN_SEATING: totalSeats (null = vehicle capacity), maskedSeatNames ignored
	// ASSIGNED_SEATING: the vehicle layout minus maskedSeatNames, totalSeats ignored
	// bookingCutoffMinutes null = default
	Trip create(
		long routeId, long vehicleId, Long departureTime, Long arrivalTime, Long price,
		SeatsType seatsType, Integer totalSeats, List<String> maskedSeatNames, Integer bookingCutoffMinutes
	);

	// only while SCHEDULED and not departed. seats type and cutoff are frozen (sending them back unchanged is fine)
	// OPEN_SEATING: totalSeats null = keep, must still hold the booked ones
	// ASSIGNED_SEATING: a vehicle swap re-copies the new layout minus maskedSeatNames, sellable seats must not shrink
	Trip update(
		long tripId, long routeId, long vehicleId, Long departureTime, Long arrivalTime, Long price,
		SeatsType seatsType, Integer totalSeats, List<String> maskedSeatNames, Integer bookingCutoffMinutes
	);

	// any time while SCHEDULED / IN_PROGRESS: holds abandoned, paid bookings cancelled with a full refund
	Trip cancel(long tripId);
}
