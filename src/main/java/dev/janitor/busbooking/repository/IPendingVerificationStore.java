package dev.janitor.busbooking.repository;

import java.util.Optional;

import dev.janitor.busbooking.model.PendingEmailVerification;

// ttl bound storage for email codes, keyed by email
public interface IPendingVerificationStore {
	// replaces any pending code and resets the attempt counter
	void put(String email, PendingEmailVerification pendingVerification, long ttlMs);
	
	Optional<PendingEmailVerification> get(String email);
	
	void delete(String email);
	
	// returns the attempt count after this failed attempt
	long recordFailedAttempt(String email, long ttlMs);
	
	// true if no cooldown was running and one has now started
	boolean tryStartResendCooldown(String email, long cooldownMs);
}
