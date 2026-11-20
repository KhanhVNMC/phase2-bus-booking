package dev.janitor.busbooking.impl.jpa.repos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.janitor.busbooking.bootstrap.HibernateRuntime;
import dev.janitor.busbooking.consts.RefundStatus;
import dev.janitor.busbooking.impl.jpa.entities.RefundEntity;
import dev.janitor.busbooking.model.Refund;
import dev.janitor.busbooking.repository.IRefundRepository;

public class JPARefundRepository implements IRefundRepository {
	private final HibernateRuntime runtime;
	
	public JPARefundRepository(HibernateRuntime runtime) {
		this.runtime = runtime;
	}
	
	@Override
	public Optional<Refund> get(long refundId) {
		try (var man = runtime.openEntityManager()) {
			RefundEntity entity = man.find(RefundEntity.class, refundId);
			return entity == null ? Optional.empty() : Optional.of(entity.toModel());
		}
	}
	
	@Override
	public Optional<Refund> getByBooking(long bookingId) {
		try (var man = runtime.openEntityManager()) {
			var query = man.createQuery("SELECT r FROM RefundEntity r WHERE r.bookingId = :booking_id", RefundEntity.class);
			query.setParameter("booking_id", bookingId);
			List<RefundEntity> matchingRefunds = query.getResultList();
			return matchingRefunds.isEmpty() ? Optional.empty() : Optional.of(matchingRefunds.getFirst().toModel());
		}
	}
	
	@Override
	public boolean refundLatePayment(long refundId, long bookingId, long createdAt) {
		return runtime.transact(man -> {
			// a webhook retried twice must not refund twice, the unique booking_id settles it
			var insert = man.createNativeQuery("""
				INSERT INTO refunds (refund_id, booking_id, amount, status, attempts, created_at, next_attempt_at, issued_at)
				SELECT :refund_id, b.booking_id, b.total_amount, 'PENDING', 0, :created_at, :created_at, 0
				FROM bookings b
				WHERE b.booking_id = :booking_id AND b.status = 'ABANDONED_PAYMENT'
				ON CONFLICT (booking_id) DO NOTHING
			""");
			insert.setParameter("refund_id", refundId);
			insert.setParameter("booking_id", bookingId);
			insert.setParameter("created_at", createdAt);
			int insertedRows = insert.executeUpdate();
			return insertedRows == 1;
		});
	}
	
	@Override
	public List<Refund> findDue(long now, int limit) {
		try (var man = runtime.openEntityManager()) {
			var query = man.createQuery("""
				SELECT r FROM RefundEntity r
				WHERE r.status = :pending AND r.nextAttemptAt <= :now
				ORDER BY r.nextAttemptAt
			""", RefundEntity.class);
			query.setParameter("pending", RefundStatus.PENDING);
			query.setParameter("now", now);
			query.setMaxResults(limit);
			List<Refund> dueRefunds = new ArrayList<>();
			for (RefundEntity entity : query.getResultList()) {
				dueRefunds.add(entity.toModel());
			}
			return dueRefunds;
		}
	}
	
	@Override
	public boolean markIssued(long refundId, long issuedAt) {
		return runtime.transact(man -> {
			var update = man.createQuery("""
				UPDATE RefundEntity r SET r.status = :issued, r.issuedAt = :issued_at, r.attempts = r.attempts + 1
				WHERE r.id = :refund_id AND r.status = :pending
			""");
			update.setParameter("issued", RefundStatus.ISSUED);
			update.setParameter("issued_at", issuedAt);
			update.setParameter("refund_id", refundId);
			update.setParameter("pending", RefundStatus.PENDING);
			int updatedRows = update.executeUpdate();
			return updatedRows == 1;
		});
	}
	
	@Override
	public boolean recordFailure(long refundId, long nextAttemptAt, int maxAttempts) {
		return runtime.transact(man -> {
			// attempts on the right side is the OLD value, so + 1 is this attempt
			var update = man.createNativeQuery("""
				UPDATE refunds SET
					attempts = attempts + 1,
					next_attempt_at = :next_attempt_at,
					status = CASE WHEN attempts + 1 >= :max_attempts THEN 'FAILED' ELSE 'PENDING' END
				WHERE refund_id = :refund_id AND status = 'PENDING'
			""");
			update.setParameter("next_attempt_at", nextAttemptAt);
			update.setParameter("max_attempts", maxAttempts);
			update.setParameter("refund_id", refundId);
			int updatedRows = update.executeUpdate();
			return updatedRows == 1;
		});
	}
}
