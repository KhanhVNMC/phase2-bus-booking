package dev.janitor.busbooking.database.redis;

import java.time.Duration;
import java.util.UUID;

/**
 * (C) 2026 gkvndev, All rights reserved.
 * Originally from Patchouli Engine (dev.gkvn.patchouli), the author's personal project.
 * Do not use, copy or redistribute outside of this project without permission.
 */
public final class RedisLeaseDispenser {
	private static final long POLL_MS = 50;
	
	private final RedisDict<String> locks;
	private final Duration lockTtl;
	
	/**
	 * Creates a family of distributed leases, one per key.
	 *
	 * @param prefix the Redis key prefix
	 * @param lockTtl how long a lease lives without renewal (a dead holder frees it after this)
	 */
	public RedisLeaseDispenser(String prefix, Duration lockTtl) {
		this.locks = new RedisDict<>(prefix, String.class);
		this.lockTtl = lockTtl;
	}
	
	/**
	 * Tries its best, within the wait, to acquire the lease.
	 *
	 * @param key the leased key
	 * @param maxWait how long to keep trying
	 * @return the lease, or {@code null} if someone still holds it after the wait
	 */
	public RedisLease acquire(String key, Duration maxWait) {
		long deadline = System.nanoTime() + maxWait.toNanos();
		do {
			RedisLease lease = this.acquireNow(key);
			if (lease != null) {
				return lease;
			}
			try {
				Thread.sleep(POLL_MS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return null;
			}
		} while (System.nanoTime() < deadline);
		return null; // sb is still holding it
	}
	
	/**
	 * Tries once to acquire the lease.
	 *
	 * @param key the leased key
	 * @return the lease, or {@code null} if someone holds it
	 */
	public RedisLease acquireNow(String key) {
		String token = UUID.randomUUID().toString();
		if (locks.putIfAbsent(key, token, lockTtl)) {
			return new RedisLease(key, token);
		}
		return null; // sb is still holding it
	}
	
	public final class RedisLease implements AutoCloseable {
		private final String key;
		private final String token;
		private boolean closed;

		private RedisLease(String key, String token) {
			this.key = key;
			this.token = token;
		}

		/**
		 * Renews the lease.
		 *
		 * @return {@code true} if the lease is still owned and was renewed
		 */
		public boolean renew() {
			return !this.closed && locks.renewIfEquals(
				this.key, this.token, lockTtl
			);
		}
		
		/**
		 * Releases the lease, only if still ours: one that expired and got taken is not ours to drop.
		 */
		@Override
		public void close() {
			if (!this.closed) {
				this.closed = true;
				locks.removeIfEquals(key, token);
			}
		}
	}
}
