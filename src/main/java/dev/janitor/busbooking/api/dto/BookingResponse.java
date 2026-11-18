package dev.janitor.busbooking.api.dto;

import java.util.List;

import dev.janitor.busbooking.consts.BookingStatus;

public record BookingResponse(
	long id,
	String bookingCode,
	long tripId,
	String passengerName,
	String passengerPhone,
	int quantity, // open count or number of assigned seats
	long unitPrice, // VND
	long totalAmount, // VND
	BookingStatus status,
	String bookedAt, // null unless CONFIRMED/CANCELLED after paying
	// ours, not in the phase 1 contract
	List<String> seats, // ASSIGNED_SEATING: seat names, empty otherwise
	String paymentStartTime
) {}
