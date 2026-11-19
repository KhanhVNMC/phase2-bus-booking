package dev.janitor.busbooking.impl.jpa.repos;

import dev.janitor.busbooking.consts.TripStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.janitor.busbooking.bootstrap.HibernateRuntime;
import dev.janitor.busbooking.consts.ActiveStatus;
import dev.janitor.busbooking.impl.jpa.entities.RouteEntity;
import dev.janitor.busbooking.model.Route;
import dev.janitor.busbooking.repository.IRouteRepository;

public class JPARouteRepository implements IRouteRepository {
	private final HibernateRuntime runtime;
	
	public JPARouteRepository(HibernateRuntime runtime) {
		this.runtime = runtime;
	}
	
	@Override
	public Optional<Route> get(long routeId) {
		try (var man = runtime.openEntityManager()) {
			RouteEntity entity = man.find(RouteEntity.class, routeId);
			return entity == null ? Optional.empty() : Optional.of(entity.toModel());
		}
	}
	
	@Override
	public List<Route> search(Long originId, Long destinationId, ActiveStatus status) {
		String jpql = "SELECT r FROM RouteEntity r WHERE 1 = 1";
		if (originId != null) jpql += " AND r.originId = :origin_id";
		if (destinationId != null) jpql += " AND r.destinationId = :destination_id";
		if (status != null) jpql += " AND r.status = :status";
		jpql += " ORDER BY r.id";
		
		try (var man = runtime.openEntityManager()) {
			var query = man.createQuery(jpql, RouteEntity.class);
			if (originId != null) query.setParameter("origin_id", originId);
			if (destinationId != null) query.setParameter("destination_id", destinationId);
			if (status != null) query.setParameter("status", status);
			List<Route> routes = new ArrayList<>();
			for (RouteEntity entity : query.getResultList()) {
				routes.add(entity.toModel());
			}
			return routes;
		}
	}
	
	@Override
	public Route create(Route route) {
		return runtime.transact(man -> {
			var entity = RouteEntity.fromModel(route);
			man.persist(entity);
			return entity.toModel();
		});
	}
	
	@Override
	public Route update(Route route) {
		return runtime.transact(man -> {
			var existing = man.find(RouteEntity.class, route.id());
			if (existing == null) {
				return null;
			}
			existing.setOriginId(route.originId());
			existing.setDestinationId(route.destinationId());
			existing.setEstimatedDurationMinutes(route.estimatedDurationMinutes());
			existing.setStatus(route.status());
			return existing.toModel();
		});
	}
	
	@Override
	public boolean hasAnyTrips(long routeId) {
		try (var man = runtime.openEntityManager()) {
			var query = man.createQuery("SELECT count(t) FROM TripEntity t WHERE t.routeId = :route_id", Long.class);
			query.setParameter("route_id", routeId);
			long trips = query.getSingleResult();
			return trips > 0;
		}
	}
	
	@Override
	public boolean hasActiveTrips(long routeId) {
		try (var man = runtime.openEntityManager()) {
			var query = man.createQuery("""
				SELECT count(t) FROM TripEntity t
				WHERE t.routeId = :route_id AND t.status IN (:scheduled, :in_progress)
			""", Long.class);
			query.setParameter("route_id", routeId);
			query.setParameter("scheduled", TripStatus.SCHEDULED);
			query.setParameter("in_progress", TripStatus.IN_PROGRESS);
			long activeTrips = query.getSingleResult();
			return activeTrips > 0;
		}
	}
	
	@Override
	public boolean existsByEndpoints(long originId, long destinationId, long excludedRouteId) {
		try (var man = runtime.openEntityManager()) {
			var query = man.createQuery("""
				SELECT count(r) FROM RouteEntity r
				WHERE r.originId = :origin_id AND r.destinationId = :destination_id AND r.id <> :excluded_route_id
			""", Long.class);
			query.setParameter("origin_id", originId);
			query.setParameter("destination_id", destinationId);
			query.setParameter("excluded_route_id", excludedRouteId);
			long matchingRoutes = query.getSingleResult();
			return matchingRoutes > 0;
		}
	}
}
