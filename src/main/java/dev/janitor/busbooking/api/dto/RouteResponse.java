package dev.janitor.busbooking.api.dto;

import dev.janitor.busbooking.consts.ActiveStatus;

public record RouteResponse(
	long id,
	LocationResponse origin,
	LocationResponse destination,
	int estimatedDurationMinutes,
	ActiveStatus status
) {}
