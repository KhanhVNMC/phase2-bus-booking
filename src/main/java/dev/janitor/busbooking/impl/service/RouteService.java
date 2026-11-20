package dev.janitor.busbooking.impl.service;

import java.util.List;

import dev.janitor.busbooking.consts.ActiveStatus;
import dev.janitor.busbooking.model.Route;
import dev.janitor.busbooking.repository.ILocationRepository;
import dev.janitor.busbooking.repository.IRouteRepository;
import dev.janitor.busbooking.service.IRouteService;
import dev.janitor.busbooking.service.ServiceException;
import dev.janitor.busbooking.utils.Snowflake;
import dev.janitor.busbooking.utils.Sputnik;

public class RouteService implements IRouteService {
	private static final int MAX_DURATION_MINUTES = 7 * 24 * 60; // a week, nothing in vietnam is longer
	
	private final IRouteRepository routeRepo;
	private final ILocationRepository locationRepo;
	
	public RouteService(IRouteRepository routeRepo, ILocationRepository locationRepo) {
		this.routeRepo = routeRepo;
		this.locationRepo = locationRepo;
	}
	
	@Override
	public Route get(long routeId) {
		var route = routeRepo.get(routeId);
		if (route.isEmpty()) {
			throw ServiceException.notFound("Route not found");
		}
		return route.get();
	}
	
	@Override
	public List<Route> search(Long originId, Long destinationId, ActiveStatus status) {
		return routeRepo.search(originId, destinationId, status);
	}
	
	@Override
	public Route create(long originId, long destinationId, Integer estimatedDurationMinutes) {
		this.requireEndpoints(originId, destinationId);
		Route route = new Route(
			Snowflake.get().nextId(),
			originId,
			destinationId,
			Sputnik.requireRange(estimatedDurationMinutes, "estimatedDurationMinutes", 1, MAX_DURATION_MINUTES),
			ActiveStatus.ACTIVE
		);
		this.requireUnique(route);
		return routeRepo.create(route);
	}
	
	@Override
	public Route update(long routeId, long originId, long destinationId, Integer estimatedDurationMinutes, ActiveStatus status) {
		Route current = this.get(routeId);
		if (status == null) {
			throw ServiceException.invalid("status is required");
		}
		boolean endpointsChanged = current.originId() != originId || current.destinationId() != destinationId;
		// if the endpoints (origin, dest) changed and the trip has been used before (past or active). refuse
		// to allow it to be edited because it is scammy so
		if (endpointsChanged) {
			this.requireEndpoints(originId, destinationId);
			if (routeRepo.hasAnyTrips(routeId)) {
				throw ServiceException.conflict("Origin and destination are frozen once the route has trips");
			}
		}
		Route route = new Route(
			routeId,
			originId,
			destinationId,
			Sputnik.requireRange(estimatedDurationMinutes, "estimatedDurationMinutes", 1, MAX_DURATION_MINUTES),
			status
		);
		this.requireUnique(route);
		boolean retiring = current.status() == ActiveStatus.ACTIVE && status == ActiveStatus.INACTIVE;
		// a route cannot be retired if people are still scheduled OR is still ON IT 
		if (retiring && routeRepo.hasActiveTrips(routeId)) {
			throw ServiceException.conflict("Route still has SCHEDULED or IN_PROGRESS trips");
		}
		return routeRepo.update(route);
	}
	
	private void requireEndpoints(long originId, long destinationId) {
		if (originId == destinationId) {
			throw ServiceException.invalid("originId and destinationId must be different");
		}
		if (locationRepo.get(originId).isEmpty()) {
			throw ServiceException.notFound("Origin location not found");
		}
		if (locationRepo.get(destinationId).isEmpty()) {
			throw ServiceException.notFound("Destination location not found");
		}
	}
	
	private void requireUnique(Route route) {
		if (routeRepo.existsByEndpoints(route.originId(), route.destinationId(), route.id())) {
			throw ServiceException.conflict("A route with this origin and destination already exists");
		}
	}
}
