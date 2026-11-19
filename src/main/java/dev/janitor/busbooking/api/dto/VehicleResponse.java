package dev.janitor.busbooking.api.dto;

import java.util.List;

import dev.janitor.busbooking.consts.VehicleStatus;

public record VehicleResponse(
	String id,
	OperatorDetails operator,
	String licensePlate,
	String vehicleType,
	int seatCapacity,
	VehicleStatus status,
	String createdAt,
	// ours, not in the phase 1 contract
	List<String> seatLabels // empty = no layout
) {}
