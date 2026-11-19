package dev.janitor.busbooking.api.dto;

import java.util.List;

import dev.janitor.busbooking.consts.SeatsType;
import dev.janitor.busbooking.consts.TripStatus;

// the whole thing is what the trip cache stores
public record TripResponse(
	String id,
	String routeId,
	String vehicleId,
	RouteDetails route,
	VehicleDetails vehicle,
	String origin,
	String destination,
	String vehicleType,
	String operatorName,
	String departureTime,
	String arrivalTime,
	long price, // VND
	int availableSeats,
	TripStatus status,
	// ours, not in the phase 1 contract
	SeatsType seatsType,
	int totalSeats,
	List<String> assignedSeatNames, // ASSIGNED_SEATING: the seat map, empty otherwise
	List<String> bookedSeatNames // ASSIGNED_SEATING: taken seats, empty otherwise
) {}
