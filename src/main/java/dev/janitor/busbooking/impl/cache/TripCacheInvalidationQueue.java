package dev.janitor.busbooking.impl.cache;

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
import dev.janitor.busbooking.model.TripPublicView;

// in process, 100% in wam. Invalidation only ever DELETEs, the next read rebuilds (never refresh: that races)
// economy class: at most one delete per trip per window, however many bookings hit it (spirit airlines; they bakrupted now)
// premium class: deleted right now (admin edits) (NOTE: on a decent airline, NOT spirit, in spirit they're gonna strap you into a rocket anyway)
public class TripCacheInvalidationQueue implements AutoCloseable {
	private final RedisDict<TripPublicView> cachedPublicViews = new RedisDict<>(
		CachedTripPublicViewRepository.VIEW_PREFIX, TripPublicView.class
	);
	private final Set<Long> economyTickets = ConcurrentHashMap.newKeySet();
	private final int flushBatchSize;
	private final ScheduledExecutorService flusher = Executors.newSingleThreadScheduledExecutor(task -> {
		Thread thread = new Thread(task, "lò vi sóng");
		thread.setDaemon(true);
		return thread;
	});
	
	public TripCacheInvalidationQueue(ApplicationConfig config) {
		this.flushBatchSize = config.getTripCacheFlushBatchSize();
		long windowMillis = config.getTripCacheFlushWindowMillis();
		flusher.scheduleWithFixedDelay(this::flushEconomy, windowMillis, windowMillis, TimeUnit.MILLISECONDS);
	}
	
	// a set, so 300 bookings on one trip inside the window are still one ticket
	public void economy(long tripId) {
		economyTickets.add(tripId);
	}
	
	public void premium(long tripId) {
		economyTickets.remove(tripId); // this delete covers it
		cachedPublicViews.removeAll(List.of(String.valueOf(tripId)));
	}
	
	private void flushEconomy() {
		try {
			while (!economyTickets.isEmpty()) {
				List<String> batch = new ArrayList<>();
				Iterator<Long> tickets = economyTickets.iterator();
				while (tickets.hasNext() && batch.size() < flushBatchSize) {
					batch.add(String.valueOf(tickets.next()));
					tickets.remove();
				}
				cachedPublicViews.removeAll(batch);
			}
		} catch (RuntimeException exception) {
			// a dead flusher thread would leave the cache stale until ttl, so log and keep going
			System.err.println("[TRIP CACHE FLUSHER] " + exception.getMessage());
		}
	}
	
	@Override
	public void close() {
		flusher.shutdownNow();
		flushEconomy(); // last window, best effort
	}
}
