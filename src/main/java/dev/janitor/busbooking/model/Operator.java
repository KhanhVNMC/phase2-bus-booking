package dev.janitor.busbooking.model;

import dev.janitor.busbooking.consts.ActiveStatus;

// a bus company
public record Operator(
	long id,
	String name,
	String address,
	String phone,
	String email,
	ActiveStatus status,
	long createdAt
) {}
