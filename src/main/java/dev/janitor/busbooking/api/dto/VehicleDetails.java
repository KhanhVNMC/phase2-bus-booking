package dev.janitor.busbooking.api.dto;

public record VehicleDetails(
	String vehicleType,
	int seatCapacity,
	String operatorName
) {}
