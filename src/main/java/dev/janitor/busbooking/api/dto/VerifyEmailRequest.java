package dev.janitor.busbooking.api.dto;

public record VerifyEmailRequest(
	String email,
	String code
) {}
