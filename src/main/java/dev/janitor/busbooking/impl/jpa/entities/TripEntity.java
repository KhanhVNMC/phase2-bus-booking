package dev.janitor.busbooking.impl.jpa.entities;

import java.util.List;

import org.hibernate.annotations.DynamicUpdate;

import dev.janitor.busbooking.consts.SeatsType;
import dev.janitor.busbooking.consts.TripStatus;
import dev.janitor.busbooking.contracts.DatabaseEntityObject;
import dev.janitor.busbooking.model.SeatSet;
import dev.janitor.busbooking.model.Trip;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// dynamic update: a dirty-checked save must never rewrite the booked counters it loaded
@DynamicUpdate
@Entity
@Table(name = "trips", indexes = {
	@Index(name = "idx_trips_route_departure", columnList = "route_id, departure_time"),
	@Index(name = "idx_trips_departure", columnList = "departure_time")
})
@Getter @Setter
@NoArgsConstructor
public class TripEntity implements DatabaseEntityObject {
	@Id
	@Column(name = "trip_id")
	private long id;
	
	@Column(name = "route_id", nullable = false)
	private long routeId;
	
	@Column(name = "vehicle_id", nullable = false)
	private long vehicleId;
	
	@Column(name = "departure_time", nullable = false)
	private long departureTime;
	
	@Column(name = "arrival_time", nullable = false)
	private long arrivalTime;
	
	@Column(name = "price", nullable = false)
	private long price;
	
	@Setter(AccessLevel.NONE)
	@Enumerated(EnumType.STRING)
	@Column(name = "seats_type", nullable = false)
	private SeatsType seatsType;
	
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private TripStatus status;
	
	@Setter(AccessLevel.NONE)
	@Column(name = "created_at", nullable = false)
	private long createdAt;
	
	@Setter(AccessLevel.NONE)
	@Column(name = "booking_cutoff_minutes", nullable = false)
	private int bookingCutoffMinutes;
	
	@Column(name = "total_seats", nullable = false)
	private int totalSeats;
	
	// order = bit index (postgres arrays keep their order). replaced only by a vehicle swap
	@Setter(AccessLevel.NONE)
	@Column(name = "assigned_seat_names", nullable = false)
	private String[] assignedSeatNames;
	
	// booked state, only ever changed by the atomic updates in the booking repo
	@Setter(AccessLevel.NONE)
	@Column(name = "open_seating_booked", nullable = false)
	private int openSeatingBooked;
	
	@Setter(AccessLevel.NONE)
	@Column(name = "assigned_seating_booked_lo", nullable = false)
	private long assignedSeatingBookedLo;
	
	@Setter(AccessLevel.NONE)
	@Column(name = "assigned_seating_booked_hi", nullable = false)
	private long assignedSeatingBookedHi;
	
	public static TripEntity fromModel(Trip model) {
		var entity = new TripEntity();
		entity.id = model.id();
		entity.routeId = model.routeId();
		entity.vehicleId = model.vehicleId();
		entity.departureTime = model.departureTime();
		entity.arrivalTime = model.arrivalTime();
		entity.price = model.price();
		entity.seatsType = model.seatsType();
		entity.status = model.status();
		entity.createdAt = model.createdAt();
		entity.bookingCutoffMinutes = model.bookingCutoffMinutes();
		entity.totalSeats = model.totalSeats();
		if (model.assignedSeatNames() == null) {
			entity.assignedSeatNames = new String[0];
		} else {
			entity.assignedSeatNames = model.assignedSeatNames().toArray(new String[0]);
		}
		entity.openSeatingBooked = model.openSeatingBooked();
		entity.assignedSeatingBookedLo = model.assignedSeatingBooked().lo();
		entity.assignedSeatingBookedHi = model.assignedSeatingBooked().hi();
		return entity;
	}
	
	public Trip toModel() {
		return new Trip(
			id, routeId, vehicleId, departureTime, arrivalTime, price,
			seatsType, status, createdAt, bookingCutoffMinutes,
			totalSeats, List.of(assignedSeatNames),
			openSeatingBooked, new SeatSet(assignedSeatingBookedLo, assignedSeatingBookedHi)
		);
	}
}
