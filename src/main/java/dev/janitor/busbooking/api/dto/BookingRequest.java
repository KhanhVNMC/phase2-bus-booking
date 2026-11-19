package dev.janitor.busbooking.api.dto;

import java.util.List;

public record BookingRequest(
	String tripId,
	String passengerName,
	String passengerPhone,
	Integer quantity, // OPEN_SEATING
	// ours, not in the phase 1 contract
	List<String> seats // ASSIGNED_SEATING: seat names
) {}
