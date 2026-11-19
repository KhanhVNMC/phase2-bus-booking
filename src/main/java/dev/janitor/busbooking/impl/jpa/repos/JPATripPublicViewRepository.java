package dev.janitor.busbooking.impl.jpa.repos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.janitor.busbooking.bootstrap.HibernateRuntime;
import dev.janitor.busbooking.impl.jpa.entities.TripEntity;
import dev.janitor.busbooking.model.TripPublicView;
import dev.janitor.busbooking.repository.ITripPublicViewRepository;

public class JPATripPublicViewRepository implements ITripPublicViewRepository {
	private final HibernateRuntime runtime;
	
	public JPATripPublicViewRepository(HibernateRuntime runtime) {
		this.runtime = runtime;
	}
	
	@Override
	public List<TripPublicView> getMany(List<Long> tripIds) {
		List<TripPublicView> views = new ArrayList<>();
		if (tripIds.isEmpty()) {
			return views;
		}
		// every join is on a primary key
		try (var man = runtime.openEntityManager()) {
			var query = man.createQuery("""
				SELECT t, origin.name, destination.name, r.estimatedDurationMinutes,
				       v.vehicleType, v.seatCapacity, operator.name
				FROM TripEntity t
				JOIN RouteEntity r ON r.id = t.routeId
				JOIN LocationEntity origin ON origin.id = r.originId
				JOIN LocationEntity destination ON destination.id = r.destinationId
				JOIN VehicleEntity v ON v.id = t.vehicleId
				JOIN OperatorEntity operator ON operator.id = v.operatorId
				WHERE t.id IN :trip_ids
			""", Object[].class);
			query.setParameter("trip_ids", tripIds);
			
			Map<Long, TripPublicView> viewsById = new HashMap<>();
			for (Object[] row : query.getResultList()) {
				TripEntity trip = (TripEntity) row[0];
				TripPublicView view = new TripPublicView( // this is a crime
					trip.toModel(),
					(String) row[1],
					(String) row[2],
					(Integer) row[3],
					(String) row[4],
					(Integer) row[5],
					(String) row[6]
				);
				viewsById.put(trip.getId(), view);
			}
			// back into the caller's order, so it doesnt turn 
			// into scrambled egg
			for (Long tripId : tripIds) {
				TripPublicView view = viewsById.get(tripId);
				if (view != null) {
					views.add(view);
				}
			}
			return views;
		}
	}
	
	@Override
	public List<Long> searchIds(Long originId, Long destinationId, Long departureFrom, Long departureTo) {
		String jpql = "SELECT t.id FROM TripEntity t JOIN RouteEntity r ON r.id = t.routeId WHERE 1 = 1";
		if (originId != null) jpql += " AND r.originId = :origin_id";
		if (destinationId != null) jpql += " AND r.destinationId = :destination_id";
		if (departureFrom != null) jpql += " AND t.departureTime >= :departure_from";
		if (departureTo != null) jpql += " AND t.departureTime < :departure_to";
		jpql += " ORDER BY t.departureTime";
		
		try (var man = runtime.openEntityManager()) {
			var query = man.createQuery(jpql, Long.class);
			if (originId != null) query.setParameter("origin_id", originId);
			if (destinationId != null) query.setParameter("destination_id", destinationId);
			if (departureFrom != null) query.setParameter("departure_from", departureFrom);
			if (departureTo != null) query.setParameter("departure_to", departureTo);
			return query.getResultList();
		}
	}
}
