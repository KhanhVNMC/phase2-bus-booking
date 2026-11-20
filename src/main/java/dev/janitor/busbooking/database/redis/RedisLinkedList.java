package dev.janitor.busbooking.database.redis;

import com.fasterxml.jackson.core.JsonProcessingException;

import dev.janitor.busbooking.bootstrap.RedisRuntime;
import dev.janitor.busbooking.utils.Sputnik;
import redis.clients.jedis.RedisClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * (C) 2026 gkvndev, All rights reserved.
 * Originally from Patchouli Engine (dev.gkvn.patchouli), the author's personal project.
 * Do not use, copy or redistribute outside of this project without permission.
 */
public final class RedisLinkedList<V> {
	private final String key;
	private final Class<V> valueClass;
	
	/**
	 * Creates a typed Redis list.
	 *
	 * @param key the Redis list key
	 * @param valueClass the stored element type
	 */
	public RedisLinkedList(String key, Class<V> valueClass) {
		this.key = key;
		this.valueClass = valueClass;
	}
	
	/**
	 * Appends a value and keeps only the newest elements.
	 *
	 * @param value the value to append
	 * @param maxSize the maximum list size
	 * @throws IllegalArgumentException if the size is invalid or serialization fails
	 */
	public void pushAndTrim(V value, int maxSize) {
		this.pushAndTrim(value, maxSize, null);
	}
	
	/**
	 * Appends a value, trims the list, and optionally refreshes its expiration.
	 *
	 * @param value the value to append
	 * @param maxSize the maximum list size
	 * @param duration the expiration duration, or {@code null} to preserve it
	 * @throws IllegalArgumentException if the size or duration is invalid, or serialization fails
	 */
	public void pushAndTrim(V value, int maxSize, Duration duration) {
		if (maxSize < 1) {
			throw new IllegalArgumentException("Maximum size must be positive");
		}
		try {
			String json = Sputnik.getObjectMapper().writeValueAsString(value);
			RedisClient client = this.client();
			client.rpush(key, json);
			// redis uses neg index, which is arr[len() - |neg|]
			client.ltrim(key, -maxSize, -1); // use neg index to avoid llen call
			if (duration != null) {
				client.pexpire(key, this.toPositiveMs(duration));
			}
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException("Could not serialize Redis value", e);
		}
	}
	
	/**
	 * Appends a value and optionally refreshes the list expiration.
	 *
	 * @param value the value to append
	 * @param duration the expiration duration, or {@code null} to preserve it
	 * @return the list size after appending the value
	 * @throws IllegalArgumentException if the duration is invalid or serialization fails
	 */
	public long push(V value, Duration duration) {
		try {
			String json = Sputnik.getObjectMapper().writeValueAsString(value);
			RedisClient client = this.client();
			long size = client.rpush(key, json);
			if (duration != null) {
				client.pexpire(key, this.toPositiveMs(duration));
			}
			return size;
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException("Could not serialize Redis value", e);
		}
	}
	
	/**
	 * Appends a value.
	 *
	 * @param value the value to append
	 * @return the list size after appending the value
	 * @throws IllegalArgumentException if serialization fails
	 */
	public long push(V value) {
		return this.push(value, null);
	}

	/**
	 * Replaces the complete list without setting an expiration.
	 */
	public void replaceAll(List<V> values) {
		RedisClient client = this.client();
		client.del(key);
		if (values.isEmpty()) {
			return;
		}
		String[] serialized = new String[values.size()];
		try {
			for (int i = 0; i < values.size(); ++i) {
				serialized[i] = Sputnik.getObjectMapper().writeValueAsString(values.get(i));
			}
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException("Could not serialize Redis value", e);
		}
		client.rpush(key, serialized);
	}
	
	/**
	 * Gets all elements from oldest to newest.
	 *
	 * @return an immutable list of stored elements
	 * @throws IllegalStateException if deserialization fails
	 */
	public List<V> getAll() {
		List<String> rawList = this.client().lrange(key, 0, -1); // get all
		List<V> list = new ArrayList<>(rawList.size());
		for (String json : rawList) {
			list.add(this.deserialize(json));
		}
		return list;
	}
	
	/**
	 * Gets an element at a Redis list index.
	 *
	 * @param index the Redis list index
	 * @return the stored element, or {@code null} if absent
	 * @throws IllegalStateException if deserialization fails
	 */
	public V getAt(long index) {
		return this.deserialize(this.client().lindex(key, index));
	}
	
	/**
	 * Gets the number of stored elements.
	 *
	 * @return the list size
	 */
	public long size() {
		return this.client().llen(key);
	}
	
	/**
	 * Deletes the list.
	 */
	public void clear() {
		this.client().del(key);
	}
	
	/**
	 * Sets the list expiration.
	 *
	 * @param duration the expiration duration
	 * @return {@code true} if the expiration was set
	 */
	public boolean expire(Duration duration) {
		return this.client().pexpire(key, this.toPositiveMs(duration)) == 1L;
	}

	/**
	 * Removes the list expiration.
	 */
	public boolean persist() {
		return this.client().persist(key) == 1L;
	}
	
	/**
	 * Gets the remaining list lifetime in seconds.
	 *
	 * @return the remaining seconds, {@code -1} without expiration, or {@code -2} if absent
	 */
	public long getTTL() {
		return this.client().ttl(key);
	}
	
	private RedisClient client() {
		return RedisRuntime.getClient();
	}
	
	private V deserialize(String json) {
		if (json == null) {
			return null;
		}
		try {
			return Sputnik.getObjectMapper().readValue(json, valueClass);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Could not deserialize Redis value", e);
		}
	}

	private long toPositiveMs(Duration duration) {
		if (duration == null || duration.isNegative() || duration.isZero()) {
			throw new IllegalArgumentException("Expiration duration must be positive");
		}
		return duration.toMillis();
	}
}
