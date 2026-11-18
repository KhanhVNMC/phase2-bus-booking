package dev.janitor.busbooking.database.redis;

import java.time.Duration;
import java.util.List;

import dev.janitor.busbooking.bootstrap.RedisRuntime;
import redis.clients.jedis.AbstractTransaction;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.params.SetParams;

/**
 * (C) 2026 gkvndev, All rights reserved.
 * Originally from Patchouli Engine (dev.gkvn.patchouli), the author's personal project.
 * Do not use, copy or redistribute outside of this project without permission.
 */
public final class RedisCounter {
	private final String prefix;

	/**
	 * Creates a Redis counter.
	 *
	 * @param prefix the Redis key prefix
	 */
	public RedisCounter(String prefix) {
		this.prefix = prefix;
	}
	
	/**
	 * Increments the counter and refreshes its expiration.
	 *
	 * @param key      the counter key
	 * @param duration the counter lifetime
	 * @return the incremented value
	 */
	public long increment(String key, Duration duration) {
		String redisKey = this.prefix + ":" + key;
		RedisClient client = RedisRuntime.getClient();
		long value = client.incr(redisKey);
		client.pexpire(redisKey, duration.toMillis());
		return value;
	}

	/**
	 * Increments the counter and removes any existing expiration.
	 */
	public long increment(String key) {
		String redisKey = this.prefix + ":" + key;
		RedisClient client = RedisRuntime.getClient();
		long value = client.incr(redisKey);
		client.persist(redisKey);
		return value;
	}
	
	/**
	 * Initializes an absent counter, then decrements it without refreshing its expiration.
	 *
	 * @param key the counter key
	 * @param initialValue the value used when the counter is absent
	 * @param duration the initial counter lifetime
	 * @return the value after decrementing
	 */
	public long decrementFrom(String key, long initialValue, Duration duration) {
		String redisKey = this.prefix + ":" + key;
		List<Object> result;
		try (AbstractTransaction transaction = RedisRuntime.getClient().multi()) {
			transaction.set(
				redisKey, Long.toString(initialValue),
				SetParams.setParams().nx().px(duration.toMillis())
			);
			transaction.decr(redisKey);
			result = transaction.exec();
		}
		if (result == null
			|| result.size() != 2
			|| !(result.getLast() instanceof Number value)
		) {
			throw new IllegalStateException("Redis returned an invalid counter response");
		}
		return value.longValue();
	}
	
	/**
	 * Sets the counter without expiration.
	 */
	public void set(String key, long value) {
		RedisRuntime.getClient().set(this.prefix + ":" + key, Long.toString(value));
	}
	
	/**
	 * Returns the current counter value.
	 *
	 * @param key the counter key
	 * @return the current value, or {@code 0} if the counter does not exist
	 */
	public long get(String key) {
		String value = RedisRuntime.getClient().get(this.prefix + ":" + key);
		return value == null ? 0 : Long.parseLong(value);
	}
	
	/**
	 * Deletes the counter.
	 *
	 * @param key the counter key
	 */
	public void clear(String key) {
		RedisRuntime.getClient().del(this.prefix + ":" + key);
	}
}
