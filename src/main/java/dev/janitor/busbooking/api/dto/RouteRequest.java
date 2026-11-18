package dev.janitor.busbooking.api.dto;

import dev.janitor.busbooking.consts.ActiveStatus;

public record RouteRequest(
	Long originId,
	Long destinationId,
	Integer estimatedDurationMinutes,
	ActiveStatus status
) {}
