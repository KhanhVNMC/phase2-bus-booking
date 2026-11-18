package dev.janitor.busbooking.repository;

import java.util.List;
import java.util.Optional;

import dev.janitor.busbooking.consts.VehicleStatus;
import dev.janitor.busbooking.model.Vehicle;

public interface IVehicleRepository {
	Optional<Vehicle> get(long vehicleId);
	
	// null filters are ignored
	List<Vehicle> search(String searchText, Long operatorId, VehicleStatus status);
	
	boolean existsByPlate(String licensePlate);
	
	Vehicle create(Vehicle vehicle);
	
	Vehicle update(Vehicle vehicle);
}
