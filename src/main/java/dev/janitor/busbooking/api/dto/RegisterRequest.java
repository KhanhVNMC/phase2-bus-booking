package dev.janitor.busbooking.api.dto;

public record RegisterRequest(
	String fullName,
	String email,
	String password,
	String phone
) {}
