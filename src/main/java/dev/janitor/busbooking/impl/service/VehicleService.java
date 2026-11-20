package dev.janitor.busbooking.impl.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import dev.janitor.busbooking.consts.ActiveStatus;
import dev.janitor.busbooking.consts.VehicleStatus;
import dev.janitor.busbooking.model.Operator;
import dev.janitor.busbooking.model.SeatSet;
import dev.janitor.busbooking.model.Vehicle;
import dev.janitor.busbooking.repository.IOperatorRepository;
import dev.janitor.busbooking.repository.IVehicleRepository;
import dev.janitor.busbooking.service.IVehicleService;
import dev.janitor.busbooking.service.ServiceException;
import dev.janitor.busbooking.utils.Snowflake;
import dev.janitor.busbooking.utils.Sputnik;

// MAINTENANCE = ours but off the road, INACTIVE = retired
// only ACTIVE vehicles get new trips
public class VehicleService implements IVehicleService {
	private final IVehicleRepository vehicleRepo;
	private final IOperatorRepository operatorRepo;
	
	public VehicleService(IVehicleRepository vehicleRepo, IOperatorRepository operatorRepo) {
		this.vehicleRepo = vehicleRepo;
		this.operatorRepo = operatorRepo;
	}
	
	@Override
	public Vehicle get(long vehicleId) {
		var vehicle = vehicleRepo.get(vehicleId);
		if (vehicle.isEmpty()) {
			throw ServiceException.notFound("Vehicle not found");
		}
		return vehicle.get();
	}
	
	@Override
	public List<Vehicle> search(String searchText, Long operatorId, VehicleStatus status) {
		return vehicleRepo.search(Sputnik.trimToNull(searchText), operatorId, status);
	}
	
	@Override
	public Vehicle create(long operatorId, String licensePlate, String vehicleType, Integer seatCapacity, List<String> seatLabels) {
		this.requireActiveOperator(operatorId);
		String plate = normalizePlate(licensePlate);
		if (vehicleRepo.existsByPlate(plate)) {
			throw ServiceException.conflict("A vehicle with this license plate already exists");
		}
		int capacity = Sputnik.requireRange(seatCapacity, "seatCapacity", 1, SeatSet.CAPACITY);
		List<String> labels = normalizeSeatLabels(seatLabels, capacity);
		Vehicle vehicle = new Vehicle(
			Snowflake.get().nextId(),
			operatorId,
			plate,
			Sputnik.requireText(vehicleType, "vehicleType", 50),
			capacity,
			labels,
			VehicleStatus.ACTIVE,
			System.currentTimeMillis()
		);
		return vehicleRepo.create(vehicle);
	}
	
	@Override
	public Vehicle update(
		long vehicleId, long operatorId, String licensePlate, String vehicleType,
		Integer seatCapacity, List<String> seatLabels, VehicleStatus status
	) {
		Vehicle current = this.get(vehicleId);
		if (status == null) {
			throw ServiceException.invalid("status is required");
		}
		// immutable forever: a bus does not become another bus, you cant just willy
		// nilly change the vehicle plate (this one is a crime? no idea) or seat capacity
		// if you buy a new bus, make a new bus, dont mutate old ones
		if (!normalizePlate(licensePlate).equals(current.licensePlate())) {
			throw ServiceException.conflict("licensePlate cannot be changed");
		}
		if (!Sputnik.requireText(vehicleType, "vehicleType", 50).equals(current.vehicleType())) {
			throw ServiceException.conflict("vehicleType cannot be changed");
		}
		if (seatCapacity == null || seatCapacity != current.seatCapacity()) {
			throw ServiceException.conflict("seatCapacity cannot be changed");
		}
		if (seatLabels != null && !normalizeSeatLabels(seatLabels, current.seatCapacity()).equals(current.seatLabels())) {
			throw ServiceException.conflict("seatLabels cannot be changed");
		}
		
		boolean operatorChanged = current.operatorId() != operatorId;
		boolean leavingActive = current.status() == VehicleStatus.ACTIVE && status != VehicleStatus.ACTIVE;
		// you cannot retire or "sell your bus" to another operator while its on duty (scheduled to go or goin)
		if ((operatorChanged || leavingActive) && vehicleRepo.isOnDuty(vehicleId)) {
			throw ServiceException.conflict("Vehicle has SCHEDULED or IN_PROGRESS trips");
		}
		// a retired bus can sit under a retired operator, anything else needs a live one
		if (operatorChanged || status != VehicleStatus.INACTIVE) {
			this.requireActiveOperator(operatorId);
		}
		Vehicle vehicle = new Vehicle(
			vehicleId, operatorId, current.licensePlate(), current.vehicleType(),
			current.seatCapacity(), current.seatLabels(), status, current.createdAt()
		);
		return vehicleRepo.update(vehicle);
	}
	
	private void requireActiveOperator(long operatorId) {
		var operator = operatorRepo.get(operatorId);
		if (operator.isEmpty()) {
			throw ServiceException.notFound("Operator not found");
		}
		Operator found = operator.get();
		if (found.status() != ActiveStatus.ACTIVE) {
			throw ServiceException.conflict("Operator is INACTIVE");
		}
	}
	
	// trimmed, uppercase, inner whitespace collapsed: "36b  123.45" -> "36B 123.45"
	private static String normalizePlate(String licensePlate) {
		String plate = Sputnik.requireText(licensePlate, "licensePlate", 20);
		plate = plate.replaceAll("\\s+", " ");
		return plate.toUpperCase(Locale.ROOT);
	}
	
	// null/empty = no layout. else exactly seatCapacity labels, trimmed, unique ignoring case
	// (bookings match seat names case insensitively, so "A-1" and "a-1" == the same seat)
	private static List<String> normalizeSeatLabels(List<String> seatLabels, int seatCapacity) {
		List<String> labels = new ArrayList<>();
		if (seatLabels == null || seatLabels.isEmpty()) {
			return labels;
		}
		if (seatLabels.size() != seatCapacity) {
			throw ServiceException.invalid("seatLabels must have exactly seatCapacity (" + seatCapacity + ") labels");
		}
		Set<String> seenLabels = new HashSet<>();
		for (String rawLabel : seatLabels) {
			String label = Sputnik.requireText(rawLabel, "seat label", 32);
			if (!seenLabels.add(Sputnik.toLower(label))) {
				throw ServiceException.invalid("Duplicate seat label: " + label);
			}
			labels.add(label);
		}
		return labels;
	}
}
