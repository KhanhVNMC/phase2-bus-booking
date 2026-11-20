package dev.janitor.busbooking.impl.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.janitor.busbooking.bootstrap.ApplicationConfig;
import dev.janitor.busbooking.consts.ActiveStatus;
import dev.janitor.busbooking.consts.SeatsType;
import dev.janitor.busbooking.consts.TripStatus;
import dev.janitor.busbooking.consts.VehicleStatus;
import dev.janitor.busbooking.model.Route;
import dev.janitor.busbooking.model.SeatSet;
import dev.janitor.busbooking.model.Trip;
import dev.janitor.busbooking.model.TripPublicView;
import dev.janitor.busbooking.model.Vehicle;
import dev.janitor.busbooking.repository.IRouteRepository;
import dev.janitor.busbooking.repository.ITripPublicViewRepository;
import dev.janitor.busbooking.repository.ITripRepository;
import dev.janitor.busbooking.repository.IVehicleRepository;
import dev.janitor.busbooking.service.ITripService;
import dev.janitor.busbooking.service.ServiceException;
import dev.janitor.busbooking.utils.Snowflake;
import dev.janitor.busbooking.utils.Sputnik;

// a trip is one-shot: one bus, one route, one departure. a CANCELLED trip is dead, no editing, no reopening
public class TripService implements ITripService {
	private static final int MAX_BOOKING_CUTOFF_MINUTES = 24 * 60; // a day before departure is already plenty

	private final ITripRepository tripRepo;
	private final ITripPublicViewRepository tripViewRepo;
	private final IRouteRepository routeRepo;
	private final IVehicleRepository vehicleRepo;
	private final int defaultBookingCutoffMinutes;
	private final int minBookingCutoffMinutes;
	private final int minSellableSeats;

	public TripService(
		ITripRepository tripRepo, ITripPublicViewRepository tripViewRepo,
		IRouteRepository routeRepo, IVehicleRepository vehicleRepo, ApplicationConfig config
	) {
		this.tripRepo = tripRepo;
		this.tripViewRepo = tripViewRepo;
		this.routeRepo = routeRepo;
		this.vehicleRepo = vehicleRepo;
		this.defaultBookingCutoffMinutes = config.getDefaultBookingCutoffMinutes();
		this.minBookingCutoffMinutes = config.getMinBookingCutoffMinutes();
		this.minSellableSeats = config.getMinSellableSeats();
	}

	@Override
	public TripPublicView getPublic(long tripId) {
		List<TripPublicView> views = tripViewRepo.getMany(List.of(tripId));
		if (views.isEmpty()) {
			throw ServiceException.notFound("Trip not found");
		}
		return views.getFirst();
	}

	@Override
	public List<TripPublicView> searchPublic(Long originId, Long destinationId, Long departureFrom, Long departureTo) {
		// which trips match is always fresh from the db, only their contents come from the cache
		List<Long> tripIds = tripViewRepo.searchIds(originId, destinationId, departureFrom, departureTo);
		return tripViewRepo.getMany(tripIds);
	}

	@Override
	public Trip get(long tripId) {
		var trip = tripRepo.get(tripId);
		if (trip.isEmpty()) {
			throw ServiceException.notFound("Trip not found");
		}
		return trip.get();
	}

	@Override
	public List<Trip> adminSearch(Long routeId, TripStatus status, Long departureFrom, Long departureTo) {
		return tripRepo.adminSearch(routeId, status, departureFrom, departureTo);
	}

	@Override
	public Trip create(
		long routeId, long vehicleId, Long departureTime, Long arrivalTime, Long price,
		SeatsType seatsType, Integer totalSeats, List<String> maskedSeatNames, Integer bookingCutoffMinutes
	) {
		long now = System.currentTimeMillis();
		requireSchedule(departureTime, arrivalTime, now);
		long unitPrice = requirePrice(price);
		if (seatsType == null) {
			throw ServiceException.invalid("seatsType is required");
		}
		int cutoffMinutes = this.defaultBookingCutoffMinutes;
		if (bookingCutoffMinutes != null) {
			cutoffMinutes = Sputnik.requireRange(
				bookingCutoffMinutes, "bookingCutoffMinutes", 
				this.minBookingCutoffMinutes, MAX_BOOKING_CUTOFF_MINUTES
			);
		}
		this.requireActiveRoute(routeId);
		Vehicle vehicle = this.requireActiveVehicle(vehicleId);
		
		int sellableSeats;
		List<String> seatNames = new ArrayList<>();
		// basically, a vehicle with layout can be both in ASSIGNED or OPEN seating mode
		// a vehicle without can only support OPEN mode, duh
		if (seatsType == SeatsType.ASSIGNED_SEATING) {
			seatNames = this.maskSeatLayout(vehicle, maskedSeatNames); // basically: dont sell these seats
			sellableSeats = seatNames.size();
		} else if (totalSeats == null) {
			sellableSeats = vehicle.seatCapacity();
		} else {
			// make sure there are at least one seat left... otherwise who are you selling to
			sellableSeats = Sputnik.requireRange(totalSeats, "totalSeats", this.minSellableSeats, vehicle.seatCapacity());
		}
		Trip trip = new Trip(Snowflake.get().nextId(),
			routeId,vehicleId,
			departureTime, arrivalTime,
			unitPrice,
			seatsType,
			TripStatus.SCHEDULED, now,
			cutoffMinutes,
			sellableSeats, seatNames,
			0, SeatSet.EMPTY // no seats booked yet
		);
		return tripRepo.create(trip);
	}

	@Override
	public Trip update(
		long tripId, long routeId, long vehicleId, Long departureTime, Long arrivalTime, Long price,
		SeatsType seatsType, Integer totalSeats, List<String> maskedSeatNames, Integer bookingCutoffMinutes
	) {
		long now = System.currentTimeMillis();
		Trip current = this.get(tripId);
		this.requireEditable(current, now);
		// frozen at creation: a trip does not switch seating, you cant just throw a person who
		// paid to seat with their family to seat in a chicken pen with a stinky random
		if (seatsType != null && seatsType != current.seatsType()) {
			throw ServiceException.conflict("seatsType cannot be changed");
		}
		// the cutoff is a promise to the buyers, you cant just willy nilly fiddle with this
		if (bookingCutoffMinutes != null && bookingCutoffMinutes != current.bookingCutoffMinutes()) {
			throw ServiceException.conflict("bookingCutoffMinutes cannot be changed");
		}
		requireSchedule(departureTime, arrivalTime, now);
		long unitPrice = requirePrice(price);
		if (routeId != current.routeId()) {
			this.requireActiveRoute(routeId);
		}
		boolean vehicleSwapped = vehicleId != current.vehicleId();
		Vehicle vehicle = vehicleSwapped ? this.requireActiveVehicle(vehicleId) : this.requireVehicle(vehicleId);

		int sellableSeats = current.totalSeats();
		List<String> seatNames = current.assignedSeatNames();
		if (current.seatsType() == SeatsType.ASSIGNED_SEATING) {
			if (vehicleSwapped) {
				// bit i now means the new layout's i-th seat. which old seat lands on which new one
				// is the operator's problem, we only guarantee every booked bit still has a seat
				seatNames = this.maskSeatLayout(vehicle, maskedSeatNames);
				sellableSeats = seatNames.size();
				if (sellableSeats < current.totalSeats()) {
					throw ServiceException.conflict(
						"The new vehicle has " + sellableSeats + " sellable seats, the trip already sells " + current.totalSeats()
					);
				}
			}
		} else if (totalSeats != null) {
			sellableSeats = Sputnik.requireRange(totalSeats, "totalSeats", this.minSellableSeats, vehicle.seatCapacity());
		} else if (current.totalSeats() > vehicle.seatCapacity()) {
			throw ServiceException.invalid("totalSeats is above the new vehicle's capacity (" + vehicle.seatCapacity() + ")");
		}
		Trip trip = new Trip(tripId, 
			routeId, vehicleId,
			departureTime,
			arrivalTime,
			unitPrice,
			current.seatsType(),
			current.status(),
			current.createdAt(),
			current.bookingCutoffMinutes(),
			sellableSeats, seatNames,
			current.openSeatingBooked(), current.assignedSeatingBooked()
		);
		// the checks above ran on a snapshot, the update rechecks the ones that can race
		if (!tripRepo.update(trip, now)) {
			Trip latest = this.get(tripId);
			this.requireEditable(latest, now); // the trip could be departed right as we change this
			throw ServiceException.conflict("The new seats cannot hold the seats already booked");
		}
		return this.get(tripId);
	}

	@Override
	public Trip cancel(long tripId) {
		this.get(tripId);
		// admins can kill a trip at any point, even 8/10 into the journey: everyone who paid gets everything back
		// this one put strain on the operators themselves, so dont fuck with it
		if (!tripRepo.cancel(tripId, System.currentTimeMillis(), Snowflake.supplier())) {
			throw ServiceException.conflict("Trip is already " + this.get(tripId).status());
		}
		return this.get(tripId);
	}

	private void requireEditable(Trip trip, long now) {
		if (trip.status() != TripStatus.SCHEDULED) {
			throw ServiceException.conflict("Only SCHEDULED trips can be changed, this one is " + trip.status());
		}
		if (trip.departureTime() <= now) {
			throw ServiceException.conflict("The trip has already departed");
		}
	}

	private static void requireSchedule(Long departureTime, Long arrivalTime, long now) {
		if (departureTime == null) {
			throw ServiceException.invalid("departureTime is required");
		}
		if (arrivalTime == null) {
			throw ServiceException.invalid("arrivalTime is required");
		}
		if (departureTime <= now) {
			throw ServiceException.invalid("departureTime must be in the future");
		}
		if (arrivalTime <= departureTime) {
			throw ServiceException.invalid("arrivalTime must be after departureTime");
		}
	}

	private static long requirePrice(Long price) {
		if (price == null) {
			throw ServiceException.invalid("price is required");
		}
		if (price < 0) {
			throw ServiceException.invalid("price must not be negative");
		}
		return price;
	}

	private Route requireActiveRoute(long routeId) {
		var route = routeRepo.get(routeId);
		if (route.isEmpty()) {
			throw ServiceException.notFound("Route not found");
		}
		Route found = route.get();
		if (found.status() != ActiveStatus.ACTIVE) {
			throw ServiceException.conflict("Route is INACTIVE");
		}
		return found;
	}

	private Vehicle requireVehicle(long vehicleId) {
		var vehicle = vehicleRepo.get(vehicleId);
		if (vehicle.isEmpty()) {
			throw ServiceException.notFound("Vehicle not found");
		}
		return vehicle.get();
	}

	// only ACTIVE vehicles get trips, MAINTENANCE / INACTIVE are off the road
	private Vehicle requireActiveVehicle(long vehicleId) {
		Vehicle vehicle = this.requireVehicle(vehicleId);
		if (vehicle.status() != VehicleStatus.ACTIVE) {
			throw ServiceException.conflict("Vehicle is " + vehicle.status());
		}
		return vehicle;
	}

	// the vehicle layout minus the masked seats, in layout order; masked seats do not exist in the trip's eyes
	// masked names match case insensitively, like bookings do
	// this sounds kinda low level, but trust me, it works
	private List<String> maskSeatLayout(Vehicle vehicle, List<String> maskedSeatNames) {
		if (!vehicle.hasSeatLayout()) {
			throw ServiceException.conflict("ASSIGNED_SEATING needs a vehicle with a seat layout");
		}
		Set<String> layoutNames = new HashSet<>();
		for (String label : vehicle.seatLabels()) {
			layoutNames.add(Sputnik.toLower(label));
		}
		Set<String> maskedNames = new HashSet<>();
		if (maskedSeatNames != null) {
			for (String rawName : maskedSeatNames) {
				String maskedName = Sputnik.toLower(Sputnik.requireText(rawName, "masked seat name", 32));
				if (!layoutNames.contains(maskedName)) {
					throw ServiceException.invalid("The vehicle has no seat named " + rawName.trim());
				}
				if (!maskedNames.add(maskedName)) {
					throw ServiceException.invalid("Duplicate masked seat name: " + rawName.trim());
				}
			}
		}
		List<String> seatNames = new ArrayList<>();
		for (String label : vehicle.seatLabels()) {
			if (!maskedNames.contains(Sputnik.toLower(label))) {
				seatNames.add(label);
			}
		}
		// what the hell are you doing??
		if (seatNames.size() < this.minSellableSeats) {
			throw ServiceException.invalid("Fewer than " + this.minSellableSeats + " seats left to sell after masking");
		}
		return seatNames;
	}
}
