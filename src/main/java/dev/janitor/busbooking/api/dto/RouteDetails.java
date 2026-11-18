package dev.janitor.busbooking.api.dto;

// origin/destination are location names
public record RouteDetails(
	String origin,
	String destination,
	int estimatedDurationMinutes
) {}
