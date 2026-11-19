package dev.janitor.busbooking.api.dto;

import dev.janitor.busbooking.consts.ActiveStatus;

public record OperatorResponse(
	String id,
	String name,
	String address,
	String phone,
	String email,
	ActiveStatus status,
	String createdAt
) {}
