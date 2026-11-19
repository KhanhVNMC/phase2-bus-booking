package dev.janitor.busbooking.service;

import java.util.List;

import dev.janitor.busbooking.consts.VehicleStatus;
import dev.janitor.busbooking.model.Vehicle;

public interface IVehicleService {
	Vehicle get(long vehicleId);
	
	// null filters are ignored
	List<Vehicle> search(String searchText, Long operatorId, VehicleStatus status);
	
	// starts ACTIVE. seatLabels null/empty = no layout (open seating only), else one label per seat
	Vehicle create(long operatorId, String licensePlate, String vehicleType, Integer seatCapacity, List<String> seatLabels);
	
	// plate, type, capacity and layout are immutable: sending them back unchanged is fine, changing them is not
	// (seatLabels null = not sent). operator changes only when not on duty; leaving ACTIVE only when not on duty
	Vehicle update(
		long vehicleId, long operatorId, String licensePlate, String vehicleType,
		Integer seatCapacity, List<String> seatLabels, VehicleStatus status
	);
}
