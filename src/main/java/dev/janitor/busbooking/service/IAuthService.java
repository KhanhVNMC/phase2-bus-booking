package dev.janitor.busbooking.service;

import dev.janitor.busbooking.consts.UserRole;
import dev.janitor.busbooking.model.User;

public interface IAuthService {
    record LoginResult(String token, long expiresInSeconds, User user) {}
    // Who is calling, straight from the token. No lookup, that's the whole point of stateless.
    record Principal(long userId, UserRole role) {
        public boolean isAdmin() {
            return role == UserRole.ADMIN || role == UserRole.SUPER_ADMIN;
        }
    }

    // Creates an unverified USER and mails a code. no login until the email is verified
    User register(String fullName, String email, String password, String phone);
    void verifyEmail(String email, String code);
    //Answers the same for unknown, verified and pending emails, so it cannot be used to probe accounts
    void resendVerification(String email);
    LoginResult login(String email, String password);
    // Barer token from the Authorization header, 401 for anything that is not a live token we signed
    Principal authenticate(String bearerToken);
    // The first SUPER_ADMIN from the BOOTSTRAP_SUPER_ADMIN_* settings, if none exists yetT, at stảtup
    // returns true if one was created.
    boolean bootstrapSuperAdmin(String email, String password, String fullName, String phone);
}
