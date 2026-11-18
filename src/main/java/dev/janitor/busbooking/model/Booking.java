package dev.janitor.busbooking.model;

import dev.janitor.busbooking.consts.BookingStatus;

public record Booking(
	long id,
	String bookingCode,
	long userId,
	long tripId,
	String passengerName,
	String passengerPhone,
	long unitPrice, // copied from trip.price
	SeatSet desiredAssignedSeats, // ASSIGNED_SEATING: bit i -> trip.assignedSeatNames[i]. blind otherwise
	int desiredOpenSeatsCount, // OPEN_SEATING. blind otherwise
	long totalAmount, // unitPrice * (OPEN_SEATING ? desiredOpenSeatsCount : desiredAssignedSeats.popcount())
	BookingStatus status,
	long paymentStartTime, // the hold gc sweeps on this
	long bookedAt, // CONFIRMED only
	long invalidatedAt // ABANDONED_PAYMENT / CANCELLED only
) {}
