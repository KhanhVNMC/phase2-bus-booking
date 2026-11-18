package dev.janitor.busbooking.api.dto;

import dev.janitor.busbooking.consts.UserRole;

public record ChangeRoleRequest(
	UserRole role
) {}
