package dev.janitor.busbooking.consts;

public enum ReserveResult {
	RESERVED,
	SEATS_UNAVAILABLE, // trip not SCHEDULED, wrong seats type, or the seats are taken/not enough left
	ALREADY_HOLDING // the user already has a PAYMENT_HOLDING booking
}
