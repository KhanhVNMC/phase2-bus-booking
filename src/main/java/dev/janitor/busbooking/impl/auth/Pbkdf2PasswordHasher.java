package dev.janitor.busbooking.impl.auth;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import dev.janitor.busbooking.repository.IPasswordHasher;

public class Pbkdf2PasswordHasher implements IPasswordHasher {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String SCHEME = "pbkdf2";
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;

    private final SecureRandom random = new SecureRandom();
    private final int iterations;

    public Pbkdf2PasswordHasher(int iterations) {
        this.iterations = iterations;
    }

    @Override
    public String hash(String rawPassword) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash = derive(rawPassword, salt, iterations);
        Base64.Encoder encoder = Base64.getEncoder();
        return SCHEME + "$" + iterations + "$" + encoder.encodeToString(salt) + "$" + encoder.encodeToString(hash);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        if (rawPassword == null || passwordHash == null) {
            return false;
        }
        String[] parts = passwordHash.split("\\$");
        if (parts.length != 4 || !parts[0].equals(SCHEME)) {
            return false;
        }
        try {
            int storedIterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(rawPassword, salt, storedIterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derive(String rawPassword, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 is not available on this JVM", e);
        } finally {
            spec.clearPassword();
        }
    }
}
