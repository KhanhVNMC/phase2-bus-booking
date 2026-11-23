package dev.janitor.busbooking.impl.mail;

import dev.janitor.busbooking.repository.IVerificationMailer;

// Fake mail sender to console standard output
public class ConsoleVerificationMailer implements IVerificationMailer {
	@Override
	public void sendVerificationCode(String email, String code, int validMinutes) {
		System.out.println("[FAKE MAIL] Verification code for " + email + ": " + code + " (valid " + validMinutes + " min)");
	}
}
