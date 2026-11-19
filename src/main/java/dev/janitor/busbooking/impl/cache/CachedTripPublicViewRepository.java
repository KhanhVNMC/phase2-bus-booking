package dev.janitor.busbooking.impl.cache;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import dev.janitor.busbooking.bootstrap.ApplicationConfig;
import dev.janitor.busbooking.consts.ServiceFailure;
import dev.janitor.busbooking.database.redis.RedisDict;
import dev.janitor.busbooking.model.TripPublicView;
import dev.janitor.busbooking.repository.ITripPublicViewRepository;
import dev.janitor.busbooking.service.ServiceException;

// reads never lock: whatever redis has is served as is
// a miss takes the trip's rebuild lease (one per trip, across all instances), rebuilds from the db, releases
// everyone else polls redis until the value shows up, or gives up with 503
public class CachedTripPublicViewRepository implements ITripPublicViewRepository {
	public static final String VIEW_PREFIX = "janitor:tripview";
	private static final String LEASE_PREFIX = "janitor:tripview:lease";
	
	private final ITripPublicViewRepository database;
	private final RedisDict<TripPublicView> cachedPublicViews = new RedisDict<>(VIEW_PREFIX, TripPublicView.class);
	private final RedisDict<String> rehydrateLeases = new RedisDict<>(LEASE_PREFIX, String.class);
	
	private final Duration viewTtl;
	private final Duration leaseTtl;
	private final long waitTimeoutMillis;
	private final long waitPollMillis;
	
	public CachedTripPublicViewRepository(ITripPublicViewRepository database, ApplicationConfig config) {
		this.database = database;
		this.viewTtl = Duration.ofSeconds(config.getTripCacheTtlSeconds());
		this.leaseTtl = Duration.ofMillis(config.getTripCacheLeaseTtlMillis());
		this.waitTimeoutMillis = config.getTripCacheWaitTimeoutMillis();
		this.waitPollMillis = config.getTripCacheWaitPollMillis();
	}
	
	@Override
	public List<Long> searchIds(Long originId, Long destinationId, Long departureFrom, Long departureTo) {
		return database.searchIds(originId, destinationId, departureFrom, departureTo);
	}
	
	@Override
	public List<TripPublicView> getMany(List<Long> tripIds) {
		Map<Long, TripPublicView> foundViews = new HashMap<>();
		Set<Long> nonexistentTrips = new HashSet<>();
		List<Long> pendingTrips = new ArrayList<>(new LinkedHashSet<>(tripIds)); // make sure no dupes
		long giveupTimestamp = System.currentTimeMillis() + this.waitTimeoutMillis;
		
		do {
			// plain read from redis, no lock needed, just read
			List<TripPublicView> cachedViews = cachedPublicViews.getAll(toKeys(pendingTrips));
			for (int i = 0; i < pendingTrips.size(); ++i) {
				if (cachedViews.get(i) != null) {
					foundViews.put(pendingTrips.get(i), cachedViews.get(i));
				}
			}
			pendingTrips.removeAll(foundViews.keySet());
			if (pendingTrips.isEmpty()) { // happy path, yay, no misses
				break;
			}
			// rebuild every miss nobody else is rebuilding
			this.rehydrate(pendingTrips, foundViews, nonexistentTrips);
			pendingTrips.removeAll(foundViews.keySet());
			pendingTrips.removeAll(nonexistentTrips); // do not try to repeatedly find junk
			if (pendingTrips.isEmpty()) {
				break;
			}
			// if we're still owing the user some, just SPIN THE SHIT (not really, its a sleep) out of the cpu
			// because if those are being built already by other callers, chances are, they will land here
			// eventually..., otherwise throw it at the user
			if (System.currentTimeMillis() >= giveupTimestamp) {
				throw new ServiceException(ServiceFailure.UNAVAILABLE, "Trip data is being rebuilt, try again");
			}
			this.sleepPoll();
		} while (true);
		
		List<TripPublicView> orderedViews = new ArrayList<>();
		for (long tripId : pendingTrips) { // do NOT resend duplicates
			TripPublicView view = foundViews.get(tripId);
			if (view != null) {
				orderedViews.add(view);
			}
		}
		return orderedViews;
	}
	
	// leases every trip it can (already leased ones are skipped), rebuilds them all in one db query,
	private int rehydrate(List<Long> tripIds, Map<Long, TripPublicView> outBuiltViews, Set<Long> outNonexistentTrips) {
		String leaseToken = UUID.randomUUID().toString();
		List<Long> leasedTrips = new ArrayList<>();
		try {
			for (Long tripId : tripIds) {
				// only build what we can touch (we leased it, nobody else was touching them)
				// this will acquire the lease to the system
				if (rehydrateLeases.putIfAbsent(String.valueOf(tripId), leaseToken, this.leaseTtl)) {
					leasedTrips.add(tripId);
				}
			}
			// we can touch nobody, they're being rebuilt already, return early
			if (leasedTrips.isEmpty()) {
				return 0;
			}
			int rehydratedViews = 0;
			// rebuild and cache each one of them into redis
			// also return it locally via a PBP map too so we can append it 
			//instead of doing another redis round trip
			for (TripPublicView view : database.getMany(leasedTrips)) {
				cachedPublicViews.put(String.valueOf(view.trip().id()), view, this.viewTtl);
				outBuiltViews.put(view.trip().id(), view);
				++rehydratedViews;
			}
			for (long tripId : leasedTrips) {
				// if user fetched for crap, return back crap, GIGO
				if (!outBuiltViews.containsKey(tripId)) {
					outNonexistentTrips.add(tripId);
				}
			}
			return rehydratedViews;
		} finally {
			// only our own leases: one that expired and got taken by someone else is not ours to drop
			for (long tripId : leasedTrips) {
				rehydrateLeases.removeIfEquals(String.valueOf(tripId), leaseToken);
			}
		}
	}
	
	private void sleepPoll() {
		// defensive programming 101
		try {
			Thread.sleep(waitPollMillis);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new ServiceException(ServiceFailure.UNAVAILABLE, "Interrupted while waiting for trip data");
		}
	}
	
	private static List<String> toKeys(List<Long> tripIds) {
		List<String> keys = new ArrayList<>();
		for (Long tripId : tripIds) {
			keys.add(String.valueOf(tripId));
		}
		return keys;
	}
}
