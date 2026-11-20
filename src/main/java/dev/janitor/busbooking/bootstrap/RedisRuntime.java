package dev.janitor.busbooking.bootstrap;

import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.util.JedisURIHelper;

import java.net.URI;
import java.util.Objects;

/**
 * (C) 2026 gkvndev, All rights reserved.
 * Originally from Patchouli Engine (dev.gkvn.patchouli), the author's personal project.
 * Do not use, copy or redistribute outside of this project without permission.
 */
public final class RedisRuntime implements AutoCloseable {
	private static volatile RedisRuntime global;
	
	public static void configureGlobal(RedisRuntime runtime) {
		global = Objects.requireNonNull(runtime, "runtime");
	}
	
	public static RedisRuntime get() {
		RedisRuntime current = global;
		if (current == null) {
			throw new IllegalStateException("Global RedisRuntime has not been configured");
		}
		return current;
	}
	
	public static RedisClient getClient() {
		return get().client();
	}
	
	private final RedisClient client;
	
	public RedisRuntime(String host, int port) {
		this.client = connect(RedisClient.builder().hostAndPort(host, port).build());
	}
	
	public RedisRuntime(ApplicationConfig config) {
		Objects.requireNonNull(config, "config");
		validateConfig(config);
		URI redisUri = URI.create(config.getRedisUrl().isBlank()
			? "redis://" + config.getRedisHost() + ":" + config.getRedisPort()
			: config.getRedisUrl()
		);
		DefaultJedisClientConfig clientConfig = DefaultJedisClientConfig.builder(redisUri)
			.connectionTimeoutMillis(config.getRedisConnectionTimeoutMs())
			.socketTimeoutMillis(config.getRedisSocketTimeoutMs())
			.clientName("janitor-redis")
		.build();
		ConnectionPoolConfig poolConfig = new ConnectionPoolConfig();
		poolConfig.setMinIdle(config.getRedisPoolMinimumIdle());
		poolConfig.setMaxIdle(config.getRedisPoolMaximumIdle());
		poolConfig.setMaxTotal(config.getRedisPoolMaximumTotal());
		poolConfig.setTestWhileIdle(true);
		this.client = connect(RedisClient.builder()
			.hostAndPort(JedisURIHelper.getHostAndPort(redisUri))
			.clientConfig(clientConfig)
			.poolConfig(poolConfig)
			.build()
		);
	}
	
	private static RedisClient connect(RedisClient client) {
		try {
			client.ping();
			return client;
		} catch (Exception e) {
			client.close();
			throw new RuntimeException("Cannot connect to Redis service", e);
		}
	}
	
	private static void validateConfig(ApplicationConfig config) {
		if (config.getRedisConnectionTimeoutMs() < 1) {
			throw new IllegalArgumentException("REDIS_CONNECTION_TIMEOUT_MS must be positive");
		}
		if (config.getRedisSocketTimeoutMs() < 1) {
			throw new IllegalArgumentException("REDIS_SOCKET_TIMEOUT_MS must be positive");
		}
		if (config.getRedisPoolMinimumIdle() < 0) {
			throw new IllegalArgumentException("REDIS_POOL_MINIMUM_IDLE cannot be negative");
		}
		if (config.getRedisPoolMaximumIdle() < 1) {
			throw new IllegalArgumentException("REDIS_POOL_MAXIMUM_IDLE must be positive");
		}
		if (config.getRedisPoolMaximumTotal() < 1) {
			throw new IllegalArgumentException("REDIS_POOL_MAXIMUM_TOTAL must be positive");
		}
		if (config.getRedisPoolMinimumIdle() > config.getRedisPoolMaximumIdle()) {
			throw new IllegalArgumentException("REDIS_POOL_MINIMUM_IDLE cannot exceed REDIS_POOL_MAXIMUM_IDLE");
		}
		if (config.getRedisPoolMaximumIdle() > config.getRedisPoolMaximumTotal()) {
			throw new IllegalArgumentException("REDIS_POOL_MAXIMUM_IDLE cannot exceed REDIS_POOL_MAXIMUM_TOTAL");
		}
	}
	
	public RedisClient client() {
		return client;
	}
	
	@Override
	public void close() {
		client.close();
	}
}
