package dev.janitor.busbooking.model;

import dev.janitor.busbooking.consts.ActiveStatus;
import dev.janitor.busbooking.consts.UserRole;

public record User(
	long id,
	String fullName,
	String email,
	String passwordHash,
	String phone,
	UserRole role,
	ActiveStatus status,
	boolean emailVerified,
	long createdAt,
	long updatedAt
) {}
