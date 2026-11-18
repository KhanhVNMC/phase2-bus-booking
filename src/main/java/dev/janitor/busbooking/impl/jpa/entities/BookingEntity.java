package dev.janitor.busbooking.impl.jpa.entities;

import dev.janitor.busbooking.consts.BookingStatus;
import dev.janitor.busbooking.contracts.DatabaseEntityObject;
import dev.janitor.busbooking.model.Booking;
import dev.janitor.busbooking.model.SeatSet;
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
@Table(name = "bookings", indexes = {
	@Index(name = "idx_bookings_user", columnList = "user_id"),
	@Index(name = "idx_bookings_trip", columnList = "trip_id"),
	// for the hold gc sweep
	@Index(name = "idx_bookings_status_payment_start", columnList = "status, payment_start_time")
})
@Getter @Setter
@NoArgsConstructor
public class BookingEntity implements DatabaseEntityObject {
	@Id
	@Column(name = "booking_id")
	private long id;
	
	@Setter(AccessLevel.NONE)
	@Column(name = "booking_code", nullable = false, unique = true)
	private String bookingCode;
	
	@Setter(AccessLevel.NONE)
	@Column(name = "user_id", nullable = false)
	private long userId;
	
	@Setter(AccessLevel.NONE)
	@Column(name = "trip_id", nullable = false)
	private long tripId;
	
	@Column(name = "passenger_name", nullable = false)
	private String passengerName;
	
	@Column(name = "passenger_phone", nullable = false)
	private String passengerPhone;
	
	@Setter(AccessLevel.NONE)
	@Column(name = "unit_price", nullable = false)
	private long unitPrice;
	
	@Setter(AccessLevel.NONE)
	@Column(name = "desired_assigned_seats_lo", nullable = false)
	private long desiredAssignedSeatsLo;
	
	@Setter(AccessLevel.NONE)
	@Column(name = "desired_assigned_seats_hi", nullable = false)
	private long desiredAssignedSeatsHi;
	
	@Setter(AccessLevel.NONE)
	@Column(name = "desired_open_seats_count", nullable = false)
	private int desiredOpenSeatsCount;
	
	@Setter(AccessLevel.NONE)
	@Column(name = "total_amount", nullable = false)
	private long totalAmount;
	
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private BookingStatus status;
	
	@Setter(AccessLevel.NONE)
	@Column(name = "payment_start_time", nullable = false)
	private long paymentStartTime;
	
	@Column(name = "booked_at", nullable = false)
	private long bookedAt;
	
	@Column(name = "invalidated_at", nullable = false)
	private long invalidatedAt;
	
	public static BookingEntity fromModel(Booking model) {
		var entity = new BookingEntity();
		entity.id = model.id();
		entity.bookingCode = model.bookingCode();
		entity.userId = model.userId();
		entity.tripId = model.tripId();
		entity.passengerName = model.passengerName();
		entity.passengerPhone = model.passengerPhone();
		entity.unitPrice = model.unitPrice();
		entity.desiredAssignedSeatsLo = model.desiredAssignedSeats().lo();
		entity.desiredAssignedSeatsHi = model.desiredAssignedSeats().hi();
		entity.desiredOpenSeatsCount = model.desiredOpenSeatsCount();
		entity.totalAmount = model.totalAmount();
		entity.status = model.status();
		entity.paymentStartTime = model.paymentStartTime();
		entity.bookedAt = model.bookedAt();
		entity.invalidatedAt = model.invalidatedAt();
		return entity;
	}
	
	public Booking toModel() {
		return new Booking(
			id, bookingCode, userId, tripId, passengerName, passengerPhone,
			unitPrice, new SeatSet(desiredAssignedSeatsLo, desiredAssignedSeatsHi), desiredOpenSeatsCount,
			totalAmount, status, paymentStartTime, bookedAt, invalidatedAt
		);
	}
}
