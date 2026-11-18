package dev.janitor.busbooking.repository;

import java.util.List;
import java.util.Optional;

import dev.janitor.busbooking.model.Refund;

// refunds are created by the booking/trip cancels, in the same transaction as the status flip
public interface IRefundRepository {
	Optional<Refund> get(long refundId);
	
	Optional<Refund> getByBooking(long bookingId);
	
	// PENDING with nextAttemptAt <= now, oldest first
	List<Refund> findDue(long now, int limit);
	
	// PENDING -> ISSUED. false = not PENDING anymore
	boolean markIssued(long refundId, long issuedAt);
	
	// attempts + 1; PENDING again at nextAttemptAt, or FAILED once attempts reaches maxAttempts
	// false = not PENDING anymore
	boolean recordFailure(long refundId, long nextAttemptAt, int maxAttempts);
}
