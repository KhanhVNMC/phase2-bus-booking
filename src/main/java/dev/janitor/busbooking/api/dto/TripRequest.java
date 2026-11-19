package dev.janitor.busbooking.api.dto;

import java.util.List;

import dev.janitor.busbooking.consts.SeatsType;

// times are vietnam local "yyyy-MM-dd'T'HH:mm:ss"
public record TripRequest(
	String routeId,
	String vehicleId,
	String departureTime,
	String arrivalTime,
	Long price, // VND
	// ours, not in the phase 1 contract
	SeatsType seatsType,
	Integer totalSeats, // OPEN_SEATING only
	List<String> maskedSeatNames, // ASSIGNED_SEATING only: vehicle seats not for sale
	Integer bookingCutoffMinutes // null = default (15), min 10, create only
) {}
