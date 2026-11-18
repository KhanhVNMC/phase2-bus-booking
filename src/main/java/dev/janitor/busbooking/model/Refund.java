package dev.janitor.busbooking.model;

import dev.janitor.busbooking.consts.RefundStatus;

// one per cancelled paid booking, issued slowly by the fake bank
public record Refund(
	long id,
	long bookingId,
	long amount, // VND, always the full booking.totalAmount
	RefundStatus status,
	int attempts,
	long createdAt,
	long nextAttemptAt, // PENDING only: the issuer skips it until then (retry backoff)
	long issuedAt // ISSUED only
) {}
