package dev.janitor.busbooking.repository;

import java.util.Optional;

import dev.janitor.busbooking.consts.UserRole;

// Stateless bearer tokens: everything needed to authorize a request is inside the signed token, no lookup.
public interface IAccessTokens {
    record AccessClaims(long userId, UserRole role, long issuedAt, long expiresAt) {}
    String issue(long userId, UserRole role);
    long ttlSeconds();
    // Empty for anything wrong: bad signature, expired, malformed. The reason is never leaked.
    Optional<AccessClaims> verify(String token);
}
