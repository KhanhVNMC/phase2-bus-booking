package dev.janitor.busbooking.model;

public record Location(
	long id,
	String name,
	String province,
	String address
) {}
