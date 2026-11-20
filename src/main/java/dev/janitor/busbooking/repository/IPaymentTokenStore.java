package dev.janitor.busbooking.repository;

import java.util.Optional;

// ttl bound payment tokens handed to the fake gateway, token -> booking id
// an expired token is just a late payment
public interface IPaymentTokenStore {
	void put(String paymentToken, long bookingId, long ttlMs);
	
	Optional<Long> getBookingId(String paymentToken);
	
	void delete(String paymentToken);
}
