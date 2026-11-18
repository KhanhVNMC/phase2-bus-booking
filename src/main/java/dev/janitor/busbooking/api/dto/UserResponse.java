package dev.janitor.busbooking.api.dto;

import dev.janitor.busbooking.consts.UserRole;

public record UserResponse(
	long id,
	String fullName,
	String email,
	String phone,
	UserRole role,
	boolean emailVerified
) {}
