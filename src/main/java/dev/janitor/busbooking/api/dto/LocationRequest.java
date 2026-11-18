package dev.janitor.busbooking.api.dto;

public record LocationRequest(
	String name,
	String province,
	String address
) {}
