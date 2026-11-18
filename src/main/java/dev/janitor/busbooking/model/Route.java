package dev.janitor.busbooking.model;

import dev.janitor.busbooking.consts.ActiveStatus;

// A -> B, no time, no bus
public record Route(
	long id,
	long originId,
	long destinationId,
	int estimatedDurationMinutes,
	ActiveStatus status
) {}
