package dev.janitor.busbooking.model;

import dev.janitor.busbooking.contracts.RemoteCompatibleObject;

// lives in redis only, the key ttl is the code expiry. the raw code is never stored
public record PendingEmailVerification(
	long userId,
	String codeDigest // hmac(email, code)
) implements RemoteCompatibleObject {}
