package dev.janitor.busbooking.api.dto;

import dev.janitor.busbooking.consts.ActiveStatus;

public record RouteRequest(
	String originId,
	String destinationId,
	Integer estimatedDurationMinutes,
	ActiveStatus status
) {}
