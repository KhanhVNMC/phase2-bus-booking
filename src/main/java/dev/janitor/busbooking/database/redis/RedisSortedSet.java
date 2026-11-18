package dev.janitor.busbooking.database.redis;

import redis.clients.jedis.params.ZRangeParams;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import dev.janitor.busbooking.bootstrap.RedisRuntime;

/**
 * (C) 2026 gkvndev, All rights reserved.
 * Originally from Patchouli Engine (dev.gkvn.patchouli), the author's personal project.
 * Do not use, copy or redistribute outside of this project without permission.
 */
public class RedisSortedSet {
	private final String key;
	private final byte[] keyBytes;

	/**
	 * Creates a Redis-backed sorted set.
	 *
	 * @param key the Redis key
	 */
	public RedisSortedSet(String key) {
		this.key = key;
		this.keyBytes = key.getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * Adds a member or updates its score.
	 */
	public void put(String member, long score) {
		RedisRuntime.getClient().zadd(this.key, score, member);
	}

	/**
	 * Gets the oldest members whose score is at most {@code maximumScore}.
	 */
	public List<String> getOldestBefore(long maximumScore, int limit) {
		List<byte[]> members = RedisRuntime.getClient().zrange(
			this.keyBytes,
			ZRangeParams.zrangeByScoreParams(
				Double.NEGATIVE_INFINITY, maximumScore
			).limit(0, limit)
		);
		List<String> result = new ArrayList<>(members.size());
		for (byte[] member : members) {
			result.add(new String(member, StandardCharsets.UTF_8));
		}
		return result;
	}

	/**
	 * Gets the current score of a member, or null if absent.
	 */
	public Double getScore(String member) {
		return RedisRuntime.getClient().zscore(this.key, member);
	}

	/**
	 * Removes a member.
	 */
	public void remove(String member) {
		RedisRuntime.getClient().zrem(this.key, member);
	}
}
