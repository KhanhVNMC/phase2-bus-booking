package dev.janitor.busbooking.impl.jpa.entities;

import dev.janitor.busbooking.consts.RefundStatus;
import dev.janitor.busbooking.contracts.DatabaseEntityObject;
import dev.janitor.busbooking.model.Refund;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "refunds", indexes = {
	// for the issuer picking the next due refund
	@Index(name = "idx_refunds_status_next_attempt", columnList = "status, next_attempt_at")
})
@Getter @Setter
@NoArgsConstructor
public class RefundEntity implements DatabaseEntityObject {
	@Id
	@Column(name = "refund_id")
	private long id;
	
	// one refund per booking, a double cancel can never pay out twice
	@Setter(AccessLevel.NONE)
	@Column(name = "booking_id", nullable = false, unique = true)
	private long bookingId;
	
	@Setter(AccessLevel.NONE)
	@Column(name = "amount", nullable = false)
	private long amount;
	
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private RefundStatus status;
	
	@Column(name = "attempts", nullable = false)
	private int attempts;
	
	@Setter(AccessLevel.NONE)
	@Column(name = "created_at", nullable = false)
	private long createdAt;
	
	@Column(name = "next_attempt_at", nullable = false)
	private long nextAttemptAt;
	
	@Column(name = "issued_at", nullable = false)
	private long issuedAt;
	
	public static RefundEntity fromModel(Refund model) {
		var entity = new RefundEntity();
		entity.id = model.id();
		entity.bookingId = model.bookingId();
		entity.amount = model.amount();
		entity.status = model.status();
		entity.attempts = model.attempts();
		entity.createdAt = model.createdAt();
		entity.nextAttemptAt = model.nextAttemptAt();
		entity.issuedAt = model.issuedAt();
		return entity;
	}
	
	public Refund toModel() {
		return new Refund(id, bookingId, amount, status, attempts, createdAt, nextAttemptAt, issuedAt);
	}
}
