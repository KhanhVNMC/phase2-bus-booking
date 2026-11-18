package dev.janitor.busbooking.api.dto;

public record CreateRouteRequest(
	Long originId,
	Long destinationId,
	Integer estimatedDurationMinutes
) {}
