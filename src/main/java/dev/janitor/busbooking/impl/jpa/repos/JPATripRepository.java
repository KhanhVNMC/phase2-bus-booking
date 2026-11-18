package dev.janitor.busbooking.impl.jpa.repos;

import jakarta.persistence.Query;
import dev.janitor.busbooking.model.Refund;
import dev.janitor.busbooking.impl.jpa.entities.RefundEntity;
import dev.janitor.busbooking.consts.SeatsType;
import dev.janitor.busbooking.consts.RefundStatus;
import java.util.function.LongSupplier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.janitor.busbooking.bootstrap.HibernateRuntime;
import dev.janitor.busbooking.consts.BookingStatus;
import dev.janitor.busbooking.consts.TripStatus;
import dev.janitor.busbooking.impl.jpa.entities.TripEntity;
import dev.janitor.busbooking.model.Trip;
import dev.janitor.busbooking.repository.ITripRepository;

public class JPATripRepository implements ITripRepository {
	
	private final HibernateRuntime runtime;
	
	public JPATripRepository(HibernateRuntime runtime) {
		this.runtime = runtime;
	}
	
	@Override
	public Optional<Trip> get(long tripId) {
		try (var man = runtime.openEntityManager()) {
			var query = man.createQuery("SELECT t FROM TripEntity t WHERE t.id = :trip_id", TripEntity.class);
			query.setParameter("trip_id", tripId);
			List<TripEntity> matchingTrips = query.getResultList();
			return matchingTrips.isEmpty() ? Optional.empty() : Optional.of(matchingTrips.getFirst().toModel());
		}
	}
	
	@Override
	public List<Trip> search(Long originId, Long destinationId, Long departureFrom, Long departureTo) {
		String jpql = "SELECT t FROM TripEntity t JOIN RouteEntity r ON r.id = t.routeId WHERE 1 = 1";
		if (originId != null) jpql += " AND r.originId = :origin_id";
		if (destinationId != null) jpql += " AND r.destinationId = :destination_id";
		if (departureFrom != null) jpql += " AND t.departureTime >= :departure_from";
		if (departureTo != null) jpql += " AND t.departureTime < :departure_to";
		jpql += " ORDER BY t.departureTime";
		
		try (var man = runtime.openEntityManager()) {
			var query = man.createQuery(jpql, TripEntity.class);
			if (originId != null) query.setParameter("origin_id", originId);
			if (destinationId != null) query.setParameter("destination_id", destinationId);
			if (departureFrom != null) query.setParameter("departure_from", departureFrom);
			if (departureTo != null) query.setParameter("departure_to", departureTo);
			return toModels(query.getResultList());
		}
	}
	
	@Override
	public List<Trip> adminSearch(Long routeId, TripStatus status, Long departureFrom, Long departureTo) {
		String jpql = "SELECT t FROM TripEntity t WHERE 1 = 1";
		if (routeId != null) jpql += " AND t.routeId = :route_id";
		if (status != null) jpql += " AND t.status = :status";
		if (departureFrom != null) jpql += " AND t.departureTime >= :departure_from";
		if (departureTo != null) jpql += " AND t.departureTime < :departure_to";
		jpql += " ORDER BY t.departureTime";
		
		try (var man = runtime.openEntityManager()) {
			var query = man.createQuery(jpql, TripEntity.class);
			if (routeId != null) query.setParameter("route_id", routeId);
			if (status != null) query.setParameter("status", status);
			if (departureFrom != null) query.setParameter("departure_from", departureFrom);
			if (departureTo != null) query.setParameter("departure_to", departureTo);
			return toModels(query.getResultList());
		}
	}
	
	private static List<Trip> toModels(List<TripEntity> entities) {
		List<Trip> trips = new ArrayList<>();
		for (TripEntity entity : entities) {
			trips.add(entity.toModel());
		}
		return trips;
	}
	
	@Override
	public Trip create(Trip trip) {
		return runtime.transact(man -> {
			var entity = TripEntity.fromModel(trip);
			man.persist(entity);
			return entity.toModel();
		});
	}
	
	@Override
	public boolean update(Trip trip, long now) {
		return runtime.transact(man -> {
			int updatedRows;
			if (trip.seatsType() == SeatsType.ASSIGNED_SEATING) {
				// booked bits are all below the old total, so a total that does not shrink keeps them valid
				var update = man.createNativeQuery("""
					UPDATE trips SET
						route_id = :route_id,
						vehicle_id = :vehicle_id,
						departure_time = :departure_time,
						arrival_time = :arrival_time,
						price = :price,
						total_seats = :total_seats,
						-- the new vehicle's layout takes over, bit i -> its i-th sellable seat
						assigned_seat_names = :assigned_seat_names
					WHERE trip_id = :trip_id
						AND status = 'SCHEDULED'
						AND seats_type = 'ASSIGNED_SEATING'
						AND departure_time > :now
						AND :total_seats >= total_seats
				""");
				setUpdateParameters(update, trip, now);
				update.setParameter("assigned_seat_names", trip.assignedSeatNames().toArray(new String[0]));
				updatedRows = update.executeUpdate();
			} else {
				var update = man.createNativeQuery("""
					UPDATE trips SET
						route_id = :route_id,
						vehicle_id = :vehicle_id,
						departure_time = :departure_time,
						arrival_time = :arrival_time,
						price = :price,
						total_seats = :total_seats
					WHERE trip_id = :trip_id
						AND status = 'SCHEDULED'
						AND seats_type = 'OPEN_SEATING'
						AND departure_time > :now
						AND open_seating_booked <= :total_seats
				""");
				setUpdateParameters(update, trip, now);
				updatedRows = update.executeUpdate();
			}
			return updatedRows == 1;
		});
	}
	
	private static void setUpdateParameters(Query update, Trip trip, long now) {
		update.setParameter("trip_id", trip.id());
		update.setParameter("route_id", trip.routeId());
		update.setParameter("vehicle_id", trip.vehicleId());
		update.setParameter("departure_time", trip.departureTime());
		update.setParameter("arrival_time", trip.arrivalTime());
		update.setParameter("price", trip.price());
		update.setParameter("total_seats", trip.totalSeats());
		update.setParameter("now", now);
	}
	
	@Override
	public boolean cancel(long tripId, long cancelledAt, LongSupplier refundIds) {
		return runtime.transact(man -> {
			// takes the trip row lock first: reserve() and update() wait behind it and then see CANCELLED
			var cancelTrip = man.createNativeQuery("""
				UPDATE trips SET status = 'CANCELLED'
				WHERE trip_id = :trip_id AND status IN ('SCHEDULED', 'IN_PROGRESS')
			""");
			cancelTrip.setParameter("trip_id", tripId);
			int cancelledTrips = cancelTrip.executeUpdate();
			if (cancelledTrips != 1) {
				return false;
			}
			// the trip is dead, its booked counters are blind from here, so seats are not given back
			var abandonHolds = man.createNativeQuery("""
				UPDATE bookings SET status = 'ABANDONED_PAYMENT', invalidated_at = :cancelled_at
				WHERE trip_id = :trip_id AND status = 'PAYMENT_HOLDING'
			""");
			abandonHolds.setParameter("cancelled_at", cancelledAt);
			abandonHolds.setParameter("trip_id", tripId);
			abandonHolds.executeUpdate();
			
			// the status flip is the gate here too: a user cancelling the same booking right now
			// either flipped it first (and queued its own refund) or does not get it
			var cancelPaid = man.createNativeQuery("""
				UPDATE bookings SET status = 'CANCELLED', invalidated_at = :cancelled_at
				WHERE trip_id = :trip_id AND status = 'CONFIRMED'
				RETURNING booking_id, total_amount
			""");
			cancelPaid.setParameter("cancelled_at", cancelledAt);
			cancelPaid.setParameter("trip_id", tripId);
			List<?> cancelledBookings = cancelPaid.getResultList();
			for (Object row : cancelledBookings) {
				Object[] columns = (Object[]) row;
				long bookingId = ((Number) columns[0]).longValue();
				long totalAmount = ((Number) columns[1]).longValue();
				var refund = new Refund( // hackers' wet dream: poke this table and we bankrupt, but i dont care
					refundIds.getAsLong(), bookingId, totalAmount, RefundStatus.PENDING,
					0, cancelledAt, cancelledAt, 0
				);
				man.persist(RefundEntity.fromModel(refund));
			}
			return true;
		});
	}
	
	@Override
	public boolean hasActiveBookings(long tripId) {
		try (var man = runtime.openEntityManager()) {
			var query = man.createQuery("""
				SELECT count(b) FROM BookingEntity b
				WHERE b.tripId = :trip_id AND b.status IN (:holding, :confirmed)
			""", Long.class);
			query.setParameter("trip_id", tripId);
			query.setParameter("holding", BookingStatus.PAYMENT_HOLDING);
			query.setParameter("confirmed", BookingStatus.CONFIRMED);
			long activeBookings = query.getSingleResult();
			return activeBookings > 0;
		}
	}
}
