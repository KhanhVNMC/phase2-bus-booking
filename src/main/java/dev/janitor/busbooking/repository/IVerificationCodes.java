package dev.janitor.busbooking.repository;

public interface IVerificationCodes {
	String generate();
	String digest(String email, String code);
}
