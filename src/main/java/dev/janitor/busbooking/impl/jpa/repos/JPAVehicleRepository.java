package dev.janitor.busbooking.impl.jpa.repos;

import dev.janitor.busbooking.consts.TripStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.janitor.busbooking.bootstrap.HibernateRuntime;
import dev.janitor.busbooking.consts.VehicleStatus;
import dev.janitor.busbooking.impl.jpa.entities.VehicleEntity;
import dev.janitor.busbooking.model.Vehicle;
import dev.janitor.busbooking.repository.IVehicleRepository;
import dev.janitor.busbooking.utils.Sputnik;

public class JPAVehicleRepository implements IVehicleRepository {
	private final HibernateRuntime runtime;
	
	public JPAVehicleRepository(HibernateRuntime runtime) {
		this.runtime = runtime;
	}
	
	@Override
	public Optional<Vehicle> get(long vehicleId) {
		// toModel touches the lazy seat labels, so it has to run before the manager closes
		try (var man = runtime.openEntityManager()) {
			VehicleEntity entity = man.find(VehicleEntity.class, vehicleId);
			return entity == null ? Optional.empty() : Optional.of(entity.toModel());
		}
	}
	
	@Override
	public List<Vehicle> search(String searchText, Long operatorId, VehicleStatus status) {
		boolean hasSearchText = searchText != null && !searchText.isBlank();
		String jpql = "SELECT v FROM VehicleEntity v WHERE 1 = 1";
		if (hasSearchText) {
			jpql += " AND (lower(v.licensePlate) LIKE :pattern ESCAPE '!'"
				+ " OR lower(function('unaccent', v.vehicleType)) LIKE :pattern ESCAPE '!')";
		}
		if (operatorId != null) jpql += " AND v.operatorId = :operator_id";
		if (status != null) jpql += " AND v.status = :status";
		jpql += " ORDER BY v.licensePlate";
		
		try (var man = runtime.openEntityManager()) {
			var query = man.createQuery(jpql, VehicleEntity.class);
			if (hasSearchText) query.setParameter("pattern", Sputnik.containing(searchText));
			if (operatorId != null) query.setParameter("operator_id", operatorId);
			if (status != null) query.setParameter("status", status);
			List<Vehicle> vehicles = new ArrayList<>();
			for (VehicleEntity entity : query.getResultList()) {
				vehicles.add(entity.toModel());
			}
			return vehicles;
		}
	}
	
	@Override
	public boolean existsByPlate(String licensePlate) {
		try (var man = runtime.openEntityManager()) {
			var query = man.createQuery(
				"SELECT count(v) FROM VehicleEntity v WHERE v.licensePlate = :license_plate", Long.class
			);
			query.setParameter("license_plate", licensePlate);
			long matchingVehicles = query.getSingleResult();
			return matchingVehicles > 0;
		}
	}
	
	@Override
	public Vehicle create(Vehicle vehicle) {
		return runtime.transact(man -> {
			var entity = VehicleEntity.fromModel(vehicle);
			man.persist(entity);
			return entity.toModel();
		});
	}
	
	@Override
	public Vehicle update(Vehicle vehicle) {
		return runtime.transact(man -> {
			var existing = man.find(VehicleEntity.class, vehicle.id());
			if (existing == null) {
				return null;
			}
			// plate, type, capacity and the seat map are immutable, only these two move
			existing.setOperatorId(vehicle.operatorId());
			existing.setStatus(vehicle.status());
			return existing.toModel();
		});
	}
	
	@Override
	public boolean isOnDuty(long vehicleId) {
		try (var man = runtime.openEntityManager()) {
			var query = man.createQuery("""
				SELECT count(t) FROM TripEntity t
				WHERE t.vehicleId = :vehicle_id AND t.status IN (:scheduled, :in_progress)
			""", Long.class);
			query.setParameter("vehicle_id", vehicleId);
			query.setParameter("scheduled", TripStatus.SCHEDULED);
			query.setParameter("in_progress", TripStatus.IN_PROGRESS);
			long activeTrips = query.getSingleResult();
			return activeTrips > 0;
		}
	}
}
