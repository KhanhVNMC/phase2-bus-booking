package dev.janitor.busbooking.api.dto;

public record LocationResponse(
	long id,
	String name,
	String province,
	String address
) {}
