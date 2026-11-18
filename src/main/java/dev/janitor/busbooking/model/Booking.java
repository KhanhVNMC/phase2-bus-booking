package dev.janitor.busbooking.model;

import dev.janitor.busbooking.consts.BookingStatus;

public record Booking(
	long id,
	String bookingCode,
	long userId,
	long tripId,
	String passengerName,
	String passengerPhone,
	// pit mode: popcount is the seat count, bits mean nothing. named mode: bit i -> trip.sellableSeatLabels[i]
	SeatSet desiredSeats,
	long unitPrice, // copied from trip.price
	long totalAmount, // unitPrice * desiredSeats.popcount()
	BookingStatus status,
	long paymentStartTime, // the hold gc sweeps on this
	long bookedAt, // CONFIRMED only, 0 otherwise
	long invalidatedAt // ABANDONED_PAYMENT / CANCELLED only, 0 otherwise
) {}
