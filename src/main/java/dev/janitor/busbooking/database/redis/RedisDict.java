package dev.janitor.busbooking.database.redis;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;

import dev.janitor.busbooking.bootstrap.RedisRuntime;
import dev.janitor.busbooking.utils.Sputnik;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.resps.ScanResult;
import redis.clients.jedis.util.CompareCondition;

/**
 * (C) 2026 gkvndev, All rights reserved.
 * Originally from Patchouli Engine (dev.gkvn.patchouli), the author's personal project.
 * Do not use, copy or redistribute outside of this project without permission.
 */
public final class RedisDict<V> {
	private static final int SCAN_BATCH_SIZE = 256;
	
	private final String prefix;
	private final Class<V> valueClass;
	
	/**
	 * Creates a typed Redis dictionary.
	 *
	 * @param prefix the Redis key prefix
	 * @param valueClass the stored value type
	 */
	public RedisDict(String prefix, Class<V> valueClass) {
		this.prefix = prefix;
		this.valueClass = valueClass;
	}
	
	/**
	 * Stores a value without expiration.
	 *
	 * @param key the dictionary key
	 * @param value the value to store
	 * @throws IllegalArgumentException if the key is null or serialization fails
	 */
	public void put(String key, V value) {
		client().set(toRedisKey(key), serialize(key, value));
	}

	/**
	 * Stores a value with expiration.
	 *
	 * @param key the dictionary key
	 * @param value the value to store
	 * @param duration the expiration duration
	 * @throws IllegalArgumentException if the key or duration is invalid, or serialization fails
	 */
	public void put(String key, V value, Duration duration) {
		client().set(
			toRedisKey(key), serialize(key, value),
			SetParams.setParams().px(toPositiveMillis(duration))
		);
	}

	/**
	 * Stores a value only when the key does not exist.
	 *
	 * @param key the dictionary key
	 * @param value the value to store
	 * @param duration the expiration duration
	 * @return {@code true} if the value was stored
	 */
	public boolean putIfAbsent(String key, V value, Duration duration) {
		String response = client().set(
			toRedisKey(key), serialize(key, value),
			SetParams.setParams().nx().px(toPositiveMillis(duration))
		);
		return "OK".equals(response);
	}
	
	/**
	 * Renews a value's expiration if its current value matches the expected value.
	 *
	 * @param key the dictionary key
	 * @param expected the expected current value
	 * @param duration the new expiration duration
	 * @return {@code true} if the expiration was renewed
	 */
	public boolean renewIfEquals(String key, V expected, Duration duration) {
		String expectedJson = serialize(key, expected);
		String response = client().set(
			toRedisKey(key), expectedJson,
			SetParams.setParams()
				.condition(CompareCondition.valueEq(expectedJson))
			.px(toPositiveMillis(duration))
		);
		return "OK".equals(response);
	}
	
	/**
	 * Replaces a value only when its current value matches the expected value.
	 *
	 * @param key the dictionary key
	 * @param expected the expected current value
	 * @param value the replacement value
	 * @param duration the new expiration duration
	 * @return {@code true} if the value was replaced
	 */
	public boolean replaceIfEquals(String key, V expected, V value, Duration duration) {
		String expectedJson = serialize(key, expected);
		String response = client().set(
			toRedisKey(key), serialize(key, value),
			SetParams.setParams()
				.condition(CompareCondition.valueEq(expectedJson))
			.px(toPositiveMillis(duration))
		);
		return "OK".equals(response);
	}

	/**
	 * Gets a stored value.
	 *
	 * @param key the dictionary key
	 * @return the stored value, or {@code null} if absent
	 * @throws IllegalStateException if deserialization fails
	 */
	public V get(String key) {
		return deserialize(key, client().get(toRedisKey(key)));
	}

	/**
	 * Gets many stored values in one round trip (MGET).
	 *
	 * @param keys the dictionary keys
	 * @return the values in the same order as the keys, {@code null} where absent
	 * @throws IllegalStateException if deserialization fails
	 */
	public List<V> getAll(List<String> keys) {
		List<V> values = new ArrayList<>();
		if (keys.isEmpty()) {
			return values;
		}
		String[] redisKeys = new String[keys.size()];
		for (int index = 0; index < keys.size(); index++) {
			redisKeys[index] = toRedisKey(keys.get(index));
		}
		List<String> jsons = client().mget(redisKeys);
		for (int index = 0; index < keys.size(); index++) {
			values.add(deserialize(keys.get(index), jsons.get(index)));
		}
		return values;
	}
	
	/**
	 * Removes many keys in one round trip (DEL).
	 *
	 * @param keys the dictionary keys
	 * @return how many keys existed and were removed
	 */
	public long removeAll(List<String> keys) {
		if (keys.isEmpty()) {
			return 0;
		}
		String[] redisKeys = new String[keys.size()];
		for (int index = 0; index < keys.size(); index++) {
			redisKeys[index] = toRedisKey(keys.get(index));
		}
		return client().del(redisKeys);
	}
	
	/**
	 * Checks whether a key exists.
	 *
	 * @param key the dictionary key
	 * @return {@code true} if the key exists
	 */
	public boolean containsKey(String key) {
		return client().exists(toRedisKey(key));
	}

	/**
	 * Removes and returns a stored value.
	 *
	 * @param key the dictionary key
	 * @return the removed value, or {@code null} if absent
	 * @throws IllegalStateException if deserialization fails
	 */
	public V remove(String key) {
		return deserialize(key, client().getDel(toRedisKey(key)));
	}

	/**
	 * Removes a value only when its current value matches the expected value.
	 *
	 * @param key the dictionary key
	 * @param expected the expected current value
	 * @return {@code true} if the value was removed
	 */
	public boolean removeIfEquals(String key, V expected) {
		String expectedJson = serialize(key, expected);
		return client().delex(
			toRedisKey(key),
			CompareCondition.valueEq(expectedJson)
		) == 1L;
	}

	/**
	 * Sets a key's expiration.
	 *
	 * @param key the dictionary key
	 * @param duration the expiration duration
	 * @return {@code true} if the expiration was set
	 */
	public boolean expire(String key, Duration duration) {
		return client().pexpire(toRedisKey(key), toPositiveMillis(duration)) == 1L;
	}

	/**
	 * Gets a key's remaining lifetime in seconds.
	 *
	 * @param key the dictionary key
	 * @return the remaining seconds, {@code -1} without expiration, or {@code -2} if absent
	 */
	public long getTTL(String key) {
		return client().ttl(toRedisKey(key));
	}

	/**
	 * Gets all matching Redis keys, including their prefix.
	 *
	 * @return the matching Redis keys
	 */
	public Set<String> keySet() {
		Set<String> keys = new HashSet<>();
		ScanParams params = new ScanParams().match(prefix + ":*").count(SCAN_BATCH_SIZE);
		String cursor = ScanParams.SCAN_POINTER_START;
		do {
			ScanResult<String> result = client().scan(cursor, params);
			keys.addAll(result.getResult());
			cursor = result.getCursor();
		} while (!ScanParams.SCAN_POINTER_START.equals(cursor));
		return keys;
	}
	
	/**
	 * Counts all matching keys.
	 *
	 * @return the number of matching keys
	 */
	public int count() {
		return keySet().size();
	}
	
	private String toRedisKey(String key) {
		if (key == null) {
			throw new IllegalArgumentException("key is null");
		}
		return prefix + ":" + key;
	}
	
	private RedisClient client() {
		return RedisRuntime.getClient();
	}

	private String serialize(String key, V value) {
		try {
			return Sputnik.getObjectMapper().writeValueAsString(value);
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException("Could not serialize Redis value for key: " + key, e);
		}
	}
	
	private V deserialize(String key, String json) {
		if (json == null) {
			return null;
		}
		try {
			return Sputnik.getObjectMapper().readValue(json, valueClass);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Could not deserialize Redis value for key: " + key, e);
		}
	}

	private long toPositiveMillis(Duration duration) {
		if (duration == null || duration.isNegative() || duration.isZero()) {
			throw new IllegalArgumentException("Expiration duration must be positive");
		}
		return duration.toMillis();
	}
}