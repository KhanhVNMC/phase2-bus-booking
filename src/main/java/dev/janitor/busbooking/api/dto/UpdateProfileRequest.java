package dev.janitor.busbooking.api.dto;

public record UpdateProfileRequest(
	String fullName,
	String phone
) {}
