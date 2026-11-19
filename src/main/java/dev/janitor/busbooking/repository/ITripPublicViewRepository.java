package dev.janitor.busbooking.repository;

import java.util.List;

import dev.janitor.busbooking.model.TripPublicView;

// the public read side of trips. may be served from a cache, the business layer does not care
public interface ITripPublicViewRepository {
	// in the same order as tripIds, trips that do not exist are left out
	List<TripPublicView> getMany(List<Long> tripIds);
	
	// public search, ordered by departure. null filters are ignored, the window is [from, to)
	// always straight from the db, so which trips match is never stale
	List<Long> searchIds(Long originId, Long destinationId, Long departureFrom, Long departureTo);
}
