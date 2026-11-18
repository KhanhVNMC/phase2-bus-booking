package dev.janitor.busbooking.impl.jpa.entities;

import java.util.List;

import dev.janitor.busbooking.consts.VehicleStatus;
import dev.janitor.busbooking.contracts.DatabaseEntityObject;
import dev.janitor.busbooking.model.Vehicle;
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

@Entity
@Table(name = "vehicles", indexes = {
	@Index(name = "idx_vehicles_operator", columnList = "operator_id")
})
@Getter @Setter
@NoArgsConstructor
public class VehicleEntity implements DatabaseEntityObject {
	@Id
	@Column(name = "vehicle_id")
	private long id;
	
	@Column(name = "operator_id", nullable = false)
	private long operatorId;
	
	@Setter(AccessLevel.NONE)
	@Column(name = "license_plate", nullable = false, unique = true)
	private String licensePlate;
	
	@Setter(AccessLevel.NONE)
	@Column(name = "vehicle_type", nullable = false)
	private String vehicleType;
	
	@Setter(AccessLevel.NONE)
	@Column(name = "seat_capacity", nullable = false)
	private int seatCapacity;
	
	// order = bit index once copied into a trip. empty = no layout, open seating only
	@Setter(AccessLevel.NONE)
	@Column(name = "seat_labels", nullable = false)
	private String[] seatLabels;
	
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private VehicleStatus status;
	
	@Setter(AccessLevel.NONE)
	@Column(name = "created_at", nullable = false)
	private long createdAt;
	
	public static VehicleEntity fromModel(Vehicle model) {
		var entity = new VehicleEntity();
		entity.id = model.id();
		entity.operatorId = model.operatorId();
		entity.licensePlate = model.licensePlate();
		entity.vehicleType = model.vehicleType();
		entity.seatCapacity = model.seatCapacity();
		if (model.seatLabels() == null) {
			entity.seatLabels = new String[0];
		} else {
			entity.seatLabels = model.seatLabels().toArray(new String[0]);
		}
		entity.status = model.status();
		entity.createdAt = model.createdAt();
		return entity;
	}
	
	public Vehicle toModel() {
		return new Vehicle(
			id, operatorId, licensePlate, vehicleType,
			seatCapacity, List.of(seatLabels), status, createdAt
		);
	}
}
