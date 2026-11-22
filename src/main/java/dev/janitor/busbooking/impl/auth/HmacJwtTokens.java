package dev.janitor.busbooking.impl.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.janitor.busbooking.consts.UserRole;
import dev.janitor.busbooking.repository.IAccessTokens;

public class HmacJwtTokens implements IAccessTokens {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MIN_KEY_BYTES = 32;
    private static final int MAX_TOKEN_LENGTH = 4096;
    private static final String HEADER = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    private final ObjectMapper json = new ObjectMapper();
    private final SecretKeySpec signingKey;
    private final String issuer;
    private final long ttlSeconds;

    public HmacJwtTokens(String base64Secret, String issuer, long ttlSeconds) {
        this.signingKey = new SecretKeySpec(decodeKey(base64Secret, "JWT_SECRET"), HMAC_ALGORITHM);
        this.issuer = issuer;
        this.ttlSeconds = ttlSeconds;
    }

    // Giải mã khóa base64
    public static byte[] decodeKey(String base64Secret, String variableName) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(base64Secret == null ? "" : base64Secret.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(variableName + " must be base64");
        }
        if (key.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(variableName + " must decode to at least " + MIN_KEY_BYTES + " bytes (openssl rand -base64 32)");
        }
        return key;
    }

    @Override
    public String issue(long userId, UserRole role) {
        long issuedAt = System.currentTimeMillis() / 1000;
        var payload = json.createObjectNode();
        payload.put("sub", String.valueOf(userId));
        payload.put("role", role.name());
        payload.put("iss", issuer);
        payload.put("iat", issuedAt);
        payload.put("exp", issuedAt + ttlSeconds);
        String signingInput = HEADER + "." + encode(payload.toString().getBytes(StandardCharsets.UTF_8));
        return signingInput + "." + encode(sign(signingInput));
    }

    @Override
    public long ttlSeconds() {
        return ttlSeconds;
    }

    @Override
    public Optional<AccessClaims> verify(String token) {
        if (token == null || token.length() > MAX_TOKEN_LENGTH) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || !parts[0].equals(HEADER)) {
            return Optional.empty();
        }
        try {
            // giai ma signature trc de biet la ai da
            byte[] expected = sign(parts[0] + "." + parts[1]);
            byte[] actual = Base64.getUrlDecoder().decode(parts[2]);
            if (!MessageDigest.isEqual(expected, actual)) {
                return Optional.empty();
            }
            JsonNode payload = json.readTree(Base64.getUrlDecoder().decode(parts[1]));
            long now = System.currentTimeMillis() / 1000;
            long issuedAt = payload.path("iat").asLong(0);
            long expiresAt = payload.path("exp").asLong(0);
            if (!issuer.equals(payload.path("iss").asText()) || expiresAt <= now || issuedAt > now) {
                return Optional.empty();
            }
            String subject = payload.path("sub").asText();
            if (!subject.matches("[1-9][0-9]{0,18}")) {
                return Optional.empty();
            }
            UserRole role = UserRole.valueOf(payload.path("role").asText());
            return Optional.of(new AccessClaims(Long.parseLong(subject), role, issuedAt, expiresAt));
        } catch (Exception e) {
            // Bad base64, bad json, unknown role, overflow: all just "not a valid token".
            return Optional.empty();
        }
    }

    private byte[] sign(String signingInput) {
        try {
            // Mac is not thread safe, a fresh one per call is cheap next to the request around it.
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(signingKey);
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available on this JVM", e);
        }
    }

    private static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
