package dev.janitor.busbooking.impl.redis;

import java.time.Duration;
import java.util.Optional;

import dev.janitor.busbooking.database.redis.RedisDict;
import dev.janitor.busbooking.repository.IPaymentTokenStore;

public class RedisPaymentTokenStore implements IPaymentTokenStore {
	private static final String TOKEN_PREFIX = "janitor:payment:token";
	
	private final RedisDict<Long> bookingIdsByToken = new RedisDict<>(TOKEN_PREFIX, Long.class);
	
	@Override
	public void put(String paymentToken, long bookingId, long ttlMillis) {
		bookingIdsByToken.put(paymentToken, bookingId, Duration.ofMillis(ttlMillis));
	}
	
	@Override
	public Optional<Long> getBookingId(String paymentToken) {
		Long bookingId = bookingIdsByToken.get(paymentToken);
		return bookingId == null ? Optional.empty() : Optional.of(bookingId);
	}
	
	@Override
	public void delete(String paymentToken) {
		bookingIdsByToken.remove(paymentToken);
	}
}
