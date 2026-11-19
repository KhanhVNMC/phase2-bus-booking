package dev.janitor.busbooking.model;

import java.util.ArrayList;
import java.util.List;

import dev.janitor.busbooking.consts.SeatsType;

// one trip plus every name a public reader needs, built by one joined query and cached as one value
// the names can be up to the cache ttl stale, the seats up to one invalidation window
public record TripPublicView(
	Trip trip,
	String originName,
	String destinationName,
	int estimatedDurationMinutes,
	String vehicleType,
	int vehicleSeatCapacity,
	String operatorName
) {
	public int availableSeats() {
		if (trip.seatsType() == SeatsType.OPEN_SEATING) {
			return trip.totalSeats() - trip.openSeatingBooked();
		}
		return trip.totalSeats() - trip.assignedSeatingBooked().popcount();
	}
	
	// ASSIGNED_SEATING: names of the taken seats, in seat order. empty for OPEN_SEATING
	public List<String> bookedSeatNames() {
		List<String> bookedNames = new ArrayList<>();
		if (trip.seatsType() != SeatsType.ASSIGNED_SEATING) {
			return bookedNames;
		}
		List<String> seatNames = trip.assignedSeatNames();
		for (int seatIndex = 0; seatIndex < seatNames.size(); seatIndex++) {
			if (trip.assignedSeatingBooked().get(seatIndex)) {
				bookedNames.add(seatNames.get(seatIndex));
			}
		}
		return bookedNames;
	}
}
