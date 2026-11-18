package dev.janitor.busbooking.api.dto;

import java.util.List;

import dev.janitor.busbooking.consts.BookingStatus;

public record BookingHistoryResponse(
	long id,
	String bookingCode,
	long tripId,
	String passengerName,
	String passengerPhone,
	int quantity,
	long totalAmount, // VND
	BookingStatus status,
	String bookedAt,
	// ours, not in the phase 1 contract
	List<String> seats, // ASSIGNED_SEATING: seat names, empty otherwise
	String paymentStartTime
) {}
