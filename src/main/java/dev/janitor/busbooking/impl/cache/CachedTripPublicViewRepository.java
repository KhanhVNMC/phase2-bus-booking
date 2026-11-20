package dev.janitor.busbooking.impl.cache;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.janitor.busbooking.bootstrap.ApplicationConfig;
import dev.janitor.busbooking.consts.ServiceFailure;
import dev.janitor.busbooking.database.redis.RedisDict;
import dev.janitor.busbooking.database.redis.RedisLeaseDispenser;
import dev.janitor.busbooking.database.redis.RedisLeaseDispenser.RedisLease;
import dev.janitor.busbooking.model.TripPublicView;
import dev.janitor.busbooking.repository.ITripPublicViewRepository;
import dev.janitor.busbooking.service.ServiceException;

// reads never lock: whatever redis has is served as is
// a miss takes the trip's rebuild lease (one per trip, across all instances), rebuilds from the db, releases
// everyone else polls redis until the value shows up, or gives up with 503
public class CachedTripPublicViewRepository implements ITripPublicViewRepository {
	public static final String VIEW_PREFIX = "janitor:tripview";
	public static final String LEASE_PREFIX = "janitor:tripview:lease";
	
	private final ITripPublicViewRepository database;
	private final RedisDict<TripPublicView> cachedPublicViews = new RedisDict<>(VIEW_PREFIX, TripPublicView.class);
	private final RedisLeaseDispenser rehydrateLeases;
	
	private final Duration viewTtl;
	private final long waitTimeoutMs;
	private final long waitPollMs;
	
	public CachedTripPublicViewRepository(ITripPublicViewRepository database, ApplicationConfig config) {
		this.database = database;
		this.viewTtl = Duration.ofSeconds(config.getTripCacheTtlSeconds());
		this.rehydrateLeases = new RedisLeaseDispenser(LEASE_PREFIX, Duration.ofMillis(config.getTripCacheLeaseTtlMs()));
		this.waitTimeoutMs = config.getTripCacheWaitTimeoutMs();
		this.waitPollMs = config.getTripCacheWaitPollMs();
	}
	
	@Override
	public List<Long> searchIds(Long originId, Long destinationId, Long departureFrom, Long departureTo) {
		return database.searchIds(originId, destinationId, departureFrom, departureTo);
	}
	
	@Override
	public List<TripPublicView> getMany(List<Long> tripIds) {
		final LinkedHashSet<Long> uniqueTrips = new LinkedHashSet<>(tripIds);  // make sure no dupes
		final long giveupTimestamp = System.currentTimeMillis() + this.waitTimeoutMs;
		
		Map<Long, TripPublicView> foundViews = new HashMap<>();
		Set<Long> nonexistentTrips = new HashSet<>();
		List<Long> pendingTrips = new ArrayList<>(uniqueTrips);
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
		for (long tripId : uniqueTrips) { // do NOT resend duplicates
			TripPublicView view = foundViews.get(tripId);
			if (view != null) {
				orderedViews.add(view);
			}
		}
		return orderedViews;
	}
	
	// leases every trip it can (already leased ones are skipped), rebuilds them all in one db query,
	private int rehydrate(List<Long> tripIds, Map<Long, TripPublicView> outBuiltViews, Set<Long> outNonexistentTrips) {
		Map<Long, RedisLease> heldLeases = new HashMap<>();
		try {
			for (Long tripId : tripIds) {
				// only build what we can touch (we leased it, nobody else was touching them)
				// this will acquire the lease to the system
				RedisLease lease = rehydrateLeases.acquireNow(String.valueOf(tripId));
				if (lease != null) {
					heldLeases.put(tripId, lease);
				}
			}
			List<Long> leasedTrips = new ArrayList<>(heldLeases.keySet());
			// we can touch nobody, they're being rebuilt already, return early
			if (leasedTrips.isEmpty()) {
				return 0;
			}
			int rehydratedViews = 0;
			Set<Long> existingTrips = new HashSet<>();
			// rebuild and cache each one of them into redis
			// also return it locally via a PBP map too so we can append it 
			//instead of doing another redis round trip
			for (TripPublicView view : database.getMany(leasedTrips)) {
				existingTrips.add(view.trip().id());
				String tripKey = String.valueOf(view.trip().id());
				// the db read may have outlived our lease, then a flusher could have taken it and deleted
				// in between: what we hold is stale, drop it. renewing also buys a fresh ttl for the put below
				if (!heldLeases.get(view.trip().id()).renew()) {
					continue; // lost the lease, thank you postgres, gg
				}
				cachedPublicViews.put(tripKey, view, this.viewTtl);
				outBuiltViews.put(view.trip().id(), view);
				++rehydratedViews;
			}
			for (long tripId : leasedTrips) {
				// if user fetched for crap, return back crap, GIGO
				// (a view dropped for a lost lease still exists, it just goes back to polling)
				if (!existingTrips.contains(tripId)) {
					outNonexistentTrips.add(tripId);
				}
			}
			return rehydratedViews;
		} finally {
			// only our own leases: one that expired and got taken by someone else is not ours to drop
			for (RedisLease lease : heldLeases.values()) {
				lease.close();
			}
		}
	}
	
	private void sleepPoll() {
		// defensive programming 101
		try {
			Thread.sleep(waitPollMs);
		} catch (InterruptedException e) {
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
