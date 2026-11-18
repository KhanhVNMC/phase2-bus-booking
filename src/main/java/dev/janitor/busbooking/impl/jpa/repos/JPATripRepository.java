package dev.janitor.busbooking.impl.jpa.repos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.janitor.busbooking.bootstrap.HibernateRuntime;
import dev.janitor.busbooking.consts.BookingStatus;
import dev.janitor.busbooking.consts.TripStatus;
import dev.janitor.busbooking.impl.jpa.entities.TripEntity;
import dev.janitor.busbooking.model.Trip;
import dev.janitor.busbooking.repository.ITripRepository;

public class JPATripRepository implements ITripRepository {
	// fetch the seat map in the same query, else every trip in a list lazy loads its own (n+1)
	// assignedSeatNames is a list, so a separate table
	private static final String SELECT_TRIPS_WITH_SEAT_NAMES =
		"SELECT DISTINCT t FROM TripEntity t LEFT JOIN FETCH t.assignedSeatNames"
	;
	
	private final HibernateRuntime runtime;
	
	public JPATripRepository(HibernateRuntime runtime) {
		this.runtime = runtime;
	}
	
	@Override
	public Optional<Trip> get(long tripId) {
		try (var man = runtime.openEntityManager()) {
			var query = man.createQuery(SELECT_TRIPS_WITH_SEAT_NAMES + " WHERE t.id = :trip_id", TripEntity.class);
			query.setParameter("trip_id", tripId);
			List<TripEntity> matchingTrips = query.getResultList();
			return matchingTrips.isEmpty() ? Optional.empty() : Optional.of(matchingTrips.getFirst().toModel());
		}
	}
	
	@Override
	public List<Trip> search(Long originId, Long destinationId, Long departureFrom, Long departureTo) {
		String jpql = SELECT_TRIPS_WITH_SEAT_NAMES + " JOIN RouteEntity r ON r.id = t.routeId WHERE 1 = 1";
		if (originId != null) jpql += " AND r.originId = :origin_id";
		if (destinationId != null) jpql += " AND r.destinationId = :destination_id";
		if (departureFrom != null) jpql += " AND t.departureTime >= :departure_from";
		if (departureTo != null) jpql += " AND t.departureTime < :departure_to";
		jpql += " ORDER BY t.departureTime";
		
		try (var man = runtime.openEntityManager()) {
			var query = man.createQuery(jpql, TripEntity.class);
			if (originId != null) query.setParameter("origin_id", originId);
			if (destinationId != null) query.setParameter("destination_id", destinationId);
			if (departureFrom != null) query.setParameter("departure_from", departureFrom);
			if (departureTo != null) query.setParameter("departure_to", departureTo);
			return toModels(query.getResultList());
		}
	}
	
	@Override
	public List<Trip> adminSearch(Long routeId, TripStatus status, Long departureFrom, Long departureTo) {
		String jpql = SELECT_TRIPS_WITH_SEAT_NAMES + " WHERE 1 = 1";
		if (routeId != null) jpql += " AND t.routeId = :route_id";
		if (status != null) jpql += " AND t.status = :status";
		if (departureFrom != null) jpql += " AND t.departureTime >= :departure_from";
		if (departureTo != null) jpql += " AND t.departureTime < :departure_to";
		jpql += " ORDER BY t.departureTime";
		
		try (var man = runtime.openEntityManager()) {
			var query = man.createQuery(jpql, TripEntity.class);
			if (routeId != null) query.setParameter("route_id", routeId);
			if (status != null) query.setParameter("status", status);
			if (departureFrom != null) query.setParameter("departure_from", departureFrom);
			if (departureTo != null) query.setParameter("departure_to", departureTo);
			return toModels(query.getResultList());
		}
	}
	
	private static List<Trip> toModels(List<TripEntity> entities) {
		List<Trip> trips = new ArrayList<>();
		for (TripEntity entity : entities) {
			trips.add(entity.toModel());
		}
		return trips;
	}
	
	@Override
	public Trip create(Trip trip) {
		return runtime.transact(man -> {
			var entity = TripEntity.fromModel(trip);
			man.persist(entity);
			return entity.toModel();
		});
	}
	
	@Override
	public boolean update(Trip trip) {
		// native on purpose: the booked state and the seat names are not in the SET list at all
		String sql = """
			UPDATE trips SET
				route_id = :route_id,
				vehicle_id = :vehicle_id,
				departure_time = :departure_time,
				arrival_time = :arrival_time,
				price = :price,
				total_seats = :total_seats,
				status = :status
			WHERE trip_id = :trip_id
			  AND open_seating_booked <= :total_seats
		""";
		return runtime.transact(man -> {
			var update = man.createNativeQuery(sql);
			update.setParameter("trip_id", trip.id());
			update.setParameter("route_id", trip.routeId());
			update.setParameter("vehicle_id", trip.vehicleId());
			update.setParameter("departure_time", trip.departureTime());
			update.setParameter("arrival_time", trip.arrivalTime());
			update.setParameter("price", trip.price());
			update.setParameter("total_seats", trip.totalSeats());
			update.setParameter("status", trip.status().name());
			int updatedRows = update.executeUpdate();
			return updatedRows == 1;
		});
	}
	
	@Override
	public boolean hasActiveBookings(long tripId) {
		try (var man = runtime.openEntityManager()) {
			var query = man.createQuery("""
				SELECT count(b) FROM BookingEntity b
				WHERE b.tripId = :trip_id AND b.status IN (:holding, :confirmed)
			""", Long.class);
			query.setParameter("trip_id", tripId);
			query.setParameter("holding", BookingStatus.PAYMENT_HOLDING);
			query.setParameter("confirmed", BookingStatus.CONFIRMED);
			long activeBookings = query.getSingleResult();
			return activeBookings > 0;
		}
	}
}
