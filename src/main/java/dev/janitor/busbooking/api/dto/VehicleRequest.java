package dev.janitor.busbooking.api.dto;

import java.util.List;

import dev.janitor.busbooking.consts.VehicleStatus;

public record VehicleRequest(
	Long operatorId,
	String licensePlate,
	String vehicleType,
	Integer seatCapacity,
	VehicleStatus status,
	// ours, not in the phase 1 contract
	List<String> seatLabels // optional layout; null = open seating only
) {}
