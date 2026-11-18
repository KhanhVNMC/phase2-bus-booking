package dev.janitor.busbooking.api.dto;

public record CreateOperatorRequest(
	String name,
	String address,
	String phone,
	String email
) {}
