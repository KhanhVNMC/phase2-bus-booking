package dev.janitor.busbooking.api.dto;

public record CreateRouteRequest(
	String originId,
	String destinationId,
	Integer estimatedDurationMinutes
) {}
