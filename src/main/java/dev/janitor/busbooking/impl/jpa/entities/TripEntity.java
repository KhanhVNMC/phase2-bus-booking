package dev.janitor.busbooking.impl.jpa.entities;

import java.util.ArrayList;
import java.util.List;

import dev.janitor.busbooking.consts.SeatingMode;
import dev.janitor.busbooking.consts.TripStatus;
import dev.janitor.busbooking.contracts.DatabaseEntityObject;
import dev.janitor.busbooking.model.SeatSet;
import dev.janitor.busbooking.model.Trip;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
	@Column(name = "seating_mode", nullable = false)
	private SeatingMode seatingMode;
	
	@Column(name = "sellable_seat_count", nullable = false)
	private int sellableSeatCount;
	
	// frozen at creation, order = bit index
	@Setter(AccessLevel.NONE)
	@ElementCollection
	@CollectionTable(name = "trip_sellable_seat_labels", joinColumns = @JoinColumn(
		name = "trip_id"
	))
	@OrderColumn(name = "seat_index")
	@Column(name = "seat_label", nullable = false)
	private List<String> sellableSeatLabels;
	
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private TripStatus status;
	
	@Setter(AccessLevel.NONE)
	@Column(name = "created_at", nullable = false)
	private long createdAt;
	
	// the seat bitset, only ever changed by the atomic update in the repo
	@Column(name = "booked_seats_lo", nullable = false)
	private long bookedSeatsLo;
	
	@Column(name = "booked_seats_hi", nullable = false)
	private long bookedSeatsHi;
	
	public static TripEntity fromModel(Trip model) {
		var entity = new TripEntity();
		entity.id = model.id();
		entity.routeId = model.routeId();
		entity.vehicleId = model.vehicleId();
		entity.departureTime = model.departureTime();
		entity.arrivalTime = model.arrivalTime();
		entity.price = model.price();
		entity.seatingMode = model.seatingMode();
		entity.sellableSeatCount = model.sellableSeatCount();
		entity.sellableSeatLabels = model.sellableSeatLabels() == null
			? new ArrayList<>() : new ArrayList<>(model.sellableSeatLabels());
		entity.status = model.status();
		entity.createdAt = model.createdAt();
		entity.bookedSeatsLo = model.bookedSeats().lo();
		entity.bookedSeatsHi = model.bookedSeats().hi();
		return entity;
	}
	
	public Trip toModel() {
		return new Trip(
			id, routeId, vehicleId, departureTime, arrivalTime, price,
			seatingMode, sellableSeatCount, List.copyOf(sellableSeatLabels),
			status, createdAt, new SeatSet(bookedSeatsLo, bookedSeatsHi)
		);
	}
}
