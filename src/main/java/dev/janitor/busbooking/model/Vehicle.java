package dev.janitor.busbooking.model;

import java.util.List;

import dev.janitor.busbooking.consts.VehicleStatus;

// one physical bus
public record Vehicle(
	long id,
	long operatorId,
	String licensePlate,
	String vehicleType,
	int seatCapacity, // == seatLabels.size() when a layout exists
	List<String> seatLabels, // optional layout; empty means open seating only
	VehicleStatus status,
	long createdAt
) {
	public boolean hasSeatLayout() {
		return seatLabels != null && !seatLabels.isEmpty();
	}
}
