package dev.janitor.busbooking.repository;

// One way, salted, slow on purpose. the hash string carries its own salt and cost.
public interface IPasswordHasher {
    String hash(String rawPassword);
    // false for a malformed hash too, never throws on bad input.
    boolean matches(String rawPassword, String passwordHash);
}
