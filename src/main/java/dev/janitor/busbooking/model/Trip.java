package dev.janitor.busbooking.model;

import java.util.List;

import dev.janitor.busbooking.consts.SeatingMode;
import dev.janitor.busbooking.consts.TripStatus;

// one bus, one route, one departure
public record Trip(
	long id,
	long routeId,
	long vehicleId,
	long departureTime,
	long arrivalTime,
	long price, // per seat, VND
	SeatingMode seatingMode,
	int sellableSeatCount,
	// copy of the vehicle layout minus the masked seats, frozen at creation; empty in pit mode
	List<String> sellableSeatLabels,
	TripStatus status,
	long createdAt,
	// pit mode: just a unary counter. named mode: bit i -> sellableSeatLabels[i]
	SeatSet bookedSeats
) {
	public boolean isFull() {
		return bookedSeats.popcount() >= sellableSeatCount;
	}
}
