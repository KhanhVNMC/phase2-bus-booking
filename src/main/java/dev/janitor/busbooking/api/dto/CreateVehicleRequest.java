package dev.janitor.busbooking.api.dto;

import java.util.List;

public record CreateVehicleRequest(
	Long operatorId,
	String licensePlate,
	String vehicleType,
	Integer seatCapacity,
	// ours, not in the phase 1 contract
	List<String> seatLabels // optional layout; null = open seating only
) {}
