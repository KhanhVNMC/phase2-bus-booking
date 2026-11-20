package dev.janitor.busbooking.repository;

import java.util.List;
import java.util.Optional;

import dev.janitor.busbooking.model.Refund;

// refunds are created by the booking/trip cancels, in the same transaction as the status flip
// (late payments are the exception, see refundLatePayment)
public interface IRefundRepository {
	Optional<Refund> get(long refundId);
	
	Optional<Refund> getByBooking(long bookingId);
	
	// a payment that landed on an ABANDONED_PAYMENT booking (the hold expired or was dropped first): full refund
	// idempotent, one refund per booking. false = not ABANDONED_PAYMENT, or already refunded
	boolean refundLatePayment(long refundId, long bookingId, long createdAt);
	
	// PENDING with nextAttemptAt <= now, oldest first
	List<Refund> findDue(long now, int limit);
	
	// PENDING -> ISSUED. false = not PENDING anymore
	boolean markIssued(long refundId, long issuedAt);
	
	// attempts + 1; PENDING again at nextAttemptAt, or FAILED once attempts reaches maxAttempts
	// false = not PENDING anymore
	boolean recordFailure(long refundId, long nextAttemptAt, int maxAttempts);
}
