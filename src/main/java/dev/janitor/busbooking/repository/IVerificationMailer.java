package dev.janitor.busbooking.repository;

// Hands the raw code to the user's inbox. Delivery is best effort, a lost mail is fixed by resending.
public interface IVerificationMailer {
    void sendVerificationCode(String email, String code, int validMinutes);
}
