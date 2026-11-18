package dev.janitor.busbooking.impl.redis;

import java.time.Duration;
import java.util.Optional;

import dev.janitor.busbooking.database.redis.RedisCounter;
import dev.janitor.busbooking.database.redis.RedisDict;
import dev.janitor.busbooking.model.PendingEmailVerification;
import dev.janitor.busbooking.repository.IPendingVerificationStore;

public class RedisPendingVerificationStore implements IPendingVerificationStore {
	private static final String PENDING_PREFIX = "janitor:verify:pending";
	private static final String ATTEMPTS_PREFIX = "janitor:verify:attempts";
	private static final String COOLDOWN_PREFIX = "janitor:verify:cooldown";
	
	private final RedisDict<PendingEmailVerification> pendingVerifications = new RedisDict<>(PENDING_PREFIX, PendingEmailVerification.class);
	private final RedisCounter failedAttempts = new RedisCounter(ATTEMPTS_PREFIX);
	private final RedisDict<Boolean> resendCooldowns = new RedisDict<>(COOLDOWN_PREFIX, Boolean.class);
	
	@Override
	public void put(String email, PendingEmailVerification pendingVerification, long ttlMillis) {
		failedAttempts.clear(email);
		pendingVerifications.put(email, pendingVerification, Duration.ofMillis(ttlMillis));
	}
	
	@Override
	public Optional<PendingEmailVerification> get(String email) {
		PendingEmailVerification pendingVerification = pendingVerifications.get(email);
		return pendingVerification == null ? Optional.empty() : Optional.of(pendingVerification);
	}
	
	@Override
	public void delete(String email) {
		pendingVerifications.remove(email);
		failedAttempts.clear(email);
	}
	
	@Override
	public long recordFailedAttempt(String email, long ttlMillis) {
		return failedAttempts.increment(email, Duration.ofMillis(ttlMillis));
	}
	
	@Override
	public boolean tryStartResendCooldown(String email, long cooldownMillis) {
		return resendCooldowns.putIfAbsent(email, true, Duration.ofMillis(cooldownMillis));
	}
}
