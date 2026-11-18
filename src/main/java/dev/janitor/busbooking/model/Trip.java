package dev.janitor.busbooking.model;

import java.util.List;

import dev.janitor.busbooking.consts.SeatsType;
import dev.janitor.busbooking.consts.TripStatus;

// one bus, one route, one departure
public record Trip(
	long id,
	long routeId,
	long vehicleId,
	long departureTime,
	long arrivalTime,
	long price, // per seat, VND
	SeatsType seatsType,
	TripStatus status,
	long createdAt,
	int bookingCutoffMinutes, // no booking/cancel after departure - this, frozen at creation
	
	int totalSeats, // kept in sync even with assigned seats (== assignedSeatNames.size())
	List<String> assignedSeatNames, // vehicle layout minus the masked seats, frozen at creation; blind if OPEN_SEATING
	
	int openSeatingBooked, // blind if ASSIGNED_SEATING
	SeatSet assignedSeatingBooked // bit i -> assignedSeatNames[i]; blind if OPEN_SEATING
) {}
