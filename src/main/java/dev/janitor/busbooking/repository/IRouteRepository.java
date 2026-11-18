package dev.janitor.busbooking.repository;

import java.util.List;
import java.util.Optional;

import dev.janitor.busbooking.consts.ActiveStatus;
import dev.janitor.busbooking.model.Route;

public interface IRouteRepository {
	Optional<Route> get(long routeId);
	
	// null filters are ignored
	List<Route> search(Long originId, Long destinationId, ActiveStatus status);
	
	Route create(Route route);
	
	Route update(Route route);
	
	// any trip at all, past ones too: freezes origin/destination
	boolean hasAnyTrips(long routeId);
	
	// any SCHEDULED or IN_PROGRESS trip: blocks going INACTIVE
	boolean hasActiveTrips(long routeId);
}
