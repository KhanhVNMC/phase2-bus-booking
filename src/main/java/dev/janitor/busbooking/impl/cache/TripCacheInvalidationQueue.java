package dev.janitor.busbooking.impl.cache;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import dev.janitor.busbooking.bootstrap.ApplicationConfig;
import dev.janitor.busbooking.database.redis.RedisDict;
import dev.janitor.busbooking.database.redis.RedisLeaseDispenser;
import dev.janitor.busbooking.database.redis.RedisLeaseDispenser.RedisLease;
import dev.janitor.busbooking.model.TripPublicView;

// in process, 100% in wam. Invalidation only ever DELETEs, the next read rebuilds (never refresh: that races)
// economy class: at most one delete per trip per window, however many bookings hit it (spirit airlines; they bakrupted now)
// both delete under the trip's rebuild lease, so a delete never lands inside a rebuild's db read -> put
// premium class: deleted right now (admin edits) (NOTE: on a decent airline, NOT spirit, in spirit they're gonna strap you into a rocket anyway)
public class TripCacheInvalidationQueue implements AutoCloseable {
	private final RedisDict<TripPublicView> cachedPublicViews = new RedisDict<>(
		CachedTripPublicViewRepository.VIEW_PREFIX, TripPublicView.class
	);
	private final RedisLeaseDispenser rehydrateLeases;
	private final Set<Long> economyTickets = ConcurrentHashMap.newKeySet();
	private final int flushBatchSize;
	private final Duration premiumSpin;
	private final ScheduledExecutorService flusher = Executors.newSingleThreadScheduledExecutor(task -> {
		Thread thread = new Thread(task, "lò vi sóng");
		thread.setDaemon(true);
		return thread;
	});
	
	public TripCacheInvalidationQueue(ApplicationConfig config) {
		this.flushBatchSize = config.getTripCacheFlushBatchSize();
		this.rehydrateLeases = new RedisLeaseDispenser(
			CachedTripPublicViewRepository.LEASE_PREFIX, Duration.ofMillis(config.getTripCacheLeaseTtlMs())
		);
		this.premiumSpin = Duration.ofMillis(config.getTripCachePremiumSpinMs());
		long windowMs = config.getTripCacheFlushWindowMs();
		flusher.scheduleWithFixedDelay(this::flushEconomy, windowMs, windowMs, TimeUnit.MILLISECONDS);
	}
	
	// a set, so 300 bookings on one trip inside the window are still one ticket
	public void economy(long tripId) {
		economyTickets.add(tripId);
	}
	
	// spins for the lease a little, a rebuild holding it ends in milliseconds
	// still taken after the spin: demoted, the flusher deletes it on a later window
	public void premium(long tripId) {
		economyTickets.remove(tripId); // this delete covers it
		String tripKey = String.valueOf(tripId);
		// to invalidate the cache, you need to acquire the lease to it first
		// this is to prevent rehydrating racing with the delete
		try (RedisLease lease = rehydrateLeases.acquire(tripKey, this.premiumSpin)) {
			if (lease == null) {
				// if acquire failed, demote it from business to economy
				this.economy(tripId);
				return;
			}
			cachedPublicViews.removeAll(List.of(tripKey));
		}
	}
	
	private void flushEconomy() {
		List<Long> unflushedTickets = new ArrayList<>();
		while (!economyTickets.isEmpty()) {
			List<Long> batch = new ArrayList<>();
			Iterator<Long> tickets = economyTickets.iterator();
			while (tickets.hasNext() && batch.size() < flushBatchSize) {
				batch.add(tickets.next());
				tickets.remove();
			}
			try {
				// leased by a rebuild right now: keep the ticket, try again next window
				unflushedTickets.addAll(this.deleteUnderLease(batch));
			} catch (RuntimeException e) {
				// a dead flusher thread would leave the cache stale until ttl, so log, keep the batch and go on
				System.err.println("[TRIP CACHE FLUSHER] " + e.getMessage());
				unflushedTickets.addAll(batch);
			}
		}
		// put back only after the drain, or the loop above would spin on them
		economyTickets.addAll(unflushedTickets);
	}
	
	// leases every trip it can, deletes those in one DEL, unleases; returns the trips it could not lease
	private List<Long> deleteUnderLease(List<Long> tripIds) {
		List<RedisLease> heldLeases = new ArrayList<>();
		List<String> leasedKeys = new ArrayList<>();
		List<Long> busyTrips = new ArrayList<>();
		try {
			for (Long tripId : tripIds) {
				String tripKey = String.valueOf(tripId);
				RedisLease lease = rehydrateLeases.acquireNow(tripKey);
				if (lease != null) {
					heldLeases.add(lease);
					leasedKeys.add(tripKey);
				} else {
					busyTrips.add(tripId);
				}
			}
			cachedPublicViews.removeAll(leasedKeys);
			return busyTrips;
		} finally {
			for (RedisLease lease : heldLeases) {
				lease.close();
			}
		}
	}
	
	@Override
	public void close() {
		flusher.shutdownNow();
		this.flushEconomy(); // last window, best effort
	}
}
