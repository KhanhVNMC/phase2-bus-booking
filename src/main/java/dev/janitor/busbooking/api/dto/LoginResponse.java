package dev.janitor.busbooking.api.dto;

public record LoginResponse(
	String token,
	String tokenType, // always "Bearer"
	long expiresIn, // seconds
	UserResponse user
) {}
