package dev.janitor.busbooking.service;

import java.util.List;

import dev.janitor.busbooking.consts.ActiveStatus;
import dev.janitor.busbooking.model.Route;

public interface IRouteService {
	Route get(long routeId);
	
	// null filters are ignored
	List<Route> search(Long originId, Long destinationId, ActiveStatus status);
	
	// starts ACTIVE
	Route create(long originId, long destinationId, Integer estimatedDurationMinutes);
	
	// origin/destination are frozen once the route has any trip; duration is always editable;
	// going INACTIVE is refused while it has SCHEDULED / IN_PROGRESS trips
	Route update(long routeId, long originId, long destinationId, Integer estimatedDurationMinutes, ActiveStatus status);
}
