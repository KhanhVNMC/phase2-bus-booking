package dev.janitor.busbooking.api.dto;

public record LocationResponse(
	String id,
	String name,
	String province,
	String address
) {}
