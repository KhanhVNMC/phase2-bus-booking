package dev.janitor.busbooking.api.dto;

public record LoginRequest(
	String email,
	String password
) {}
