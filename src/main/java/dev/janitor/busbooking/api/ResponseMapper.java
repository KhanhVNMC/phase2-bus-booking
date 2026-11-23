package dev.janitor.busbooking.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.janitor.busbooking.api.dto.BookingHistoryResponse;
import dev.janitor.busbooking.api.dto.BookingResponse;
import dev.janitor.busbooking.api.dto.LocationResponse;
import dev.janitor.busbooking.api.dto.OperatorDetails;
import dev.janitor.busbooking.api.dto.OperatorResponse;
import dev.janitor.busbooking.api.dto.RouteDetails;
import dev.janitor.busbooking.api.dto.RouteResponse;
import dev.janitor.busbooking.api.dto.TripResponse;
import dev.janitor.busbooking.api.dto.UserResponse;
import dev.janitor.busbooking.api.dto.VehicleDetails;
import dev.janitor.busbooking.api.dto.VehicleResponse;
import dev.janitor.busbooking.consts.SeatsType;
import dev.janitor.busbooking.model.Booking;
import dev.janitor.busbooking.model.Location;
import dev.janitor.busbooking.model.Operator;
import dev.janitor.busbooking.model.Route;
import dev.janitor.busbooking.model.Trip;
import dev.janitor.busbooking.model.TripPublicView;
import dev.janitor.busbooking.model.User;
import dev.janitor.busbooking.model.Vehicle;
import dev.janitor.busbooking.service.ILocationService;
import dev.janitor.busbooking.service.IOperatorService;
import dev.janitor.busbooking.service.ITripService;
import dev.janitor.busbooking.utils.Sputnik;

// model -> phase 1 shaped dto
// the lookups here (names behind ids) are admin OR low traffic paths
// the public ones come from cached so it wont fucking explode
public class ResponseMapper {
	private final ILocationService locationService;
	private final IOperatorService operatorService;
	private final ITripService tripService;
	
	public ResponseMapper(ILocationService locationService, IOperatorService operatorService, ITripService tripService) {
		this.locationService = locationService;
		this.operatorService = operatorService;
		this.tripService = tripService;
	}
	
	public static UserResponse user(User user) {
		return new UserResponse(
			Sputnik.i64ToString(user.id()), user.fullName(), user.email(), user.phone(), user.role(), user.emailVerified()
		);
	}
	
	public static LocationResponse location(Location location) {
		return new LocationResponse(Sputnik.i64ToString(location.id()), location.name(), location.province(), location.address());
	}
	
	public static OperatorResponse operator(Operator operator) {
		return new OperatorResponse(
			Sputnik.i64ToString(operator.id()), operator.name(), operator.address(), operator.phone(), operator.email(),
			operator.status(), Sputnik.timeFmtLocal(operator.createdAt())
		);
	}
	
	public RouteResponse route(Route route) {
		return new RouteResponse(
			Sputnik.i64ToString(route.id()),
			location(this.locationService.get(route.originId())),
			location(this.locationService.get(route.destinationId())),
			route.estimatedDurationMinutes(),
			route.status()
		);
	}
	
	public VehicleResponse vehicle(Vehicle vehicle) {
		Operator operator = this.operatorService.get(vehicle.operatorId());
		return new VehicleResponse(
			Sputnik.i64ToString(vehicle.id()),
			new OperatorDetails(Sputnik.i64ToString(operator.id()), operator.name()),
			vehicle.licensePlate(),
			vehicle.vehicleType(),
			vehicle.seatCapacity(),
			vehicle.status(),
			Sputnik.timeFmtLocal(vehicle.createdAt()),
			vehicle.seatLabels()
		);
	}
	
	public static TripResponse trip(TripPublicView view) {
		Trip trip = view.trip();
		return new TripResponse(
			Sputnik.i64ToString(trip.id()),
			Sputnik.i64ToString(trip.routeId()),
			Sputnik.i64ToString(trip.vehicleId()),
			new RouteDetails(view.originName(), view.destinationName(), view.estimatedDurationMinutes()),
			new VehicleDetails(view.vehicleType(), view.vehicleSeatCapacity(), view.operatorName()),
			view.originName(), view.destinationName(),
			view.vehicleType(), view.operatorName(),
			Sputnik.timeFmtLocal(trip.departureTime()),
			Sputnik.timeFmtLocal(trip.arrivalTime()),
			trip.price(), view.availableSeats(),
			trip.status(), trip.seatsType(),
			trip.totalSeats(), trip.seatsType() == SeatsType.ASSIGNED_SEATING ? trip.assignedSeatNames() : List.of(),
			view.bookedSeatNames()
		);
	}
	
	public static List<TripResponse> trips(List<TripPublicView> views) {
		List<TripResponse> responses = new ArrayList<>();
		for (TripPublicView view : views) {
			responses.add(trip(view));
		}
		return responses;
	}
	
	// paymentUrl null everywhere except the response that created the hold
	public BookingResponse booking(Booking booking, String paymentUrl) {
		return this.booking(booking, paymentUrl, new HashMap<>());
	}
	
	public List<BookingResponse> bookings(List<Booking> bookings) {
		Map<Long, Trip> tripsById = new HashMap<>();
		List<BookingResponse> responses = new ArrayList<>();
		for (Booking booking : bookings) {
			responses.add(this.booking(booking, null, tripsById));
		}
		return responses;
	}
	
	public List<BookingHistoryResponse> history(List<Booking> bookings) {
		Map<Long, Trip> tripsById = new HashMap<>();
		List<BookingHistoryResponse> responses = new ArrayList<>();
		for (Booking booking : bookings) {
			Trip trip = this.tripOf(booking, tripsById);
			responses.add(new BookingHistoryResponse(
				Sputnik.i64ToString(booking.id()), booking.bookingCode(),
				Sputnik.i64ToString(booking.tripId()),
				booking.passengerName(), booking.passengerPhone(),
				quantity(booking, trip),
				booking.totalAmount(),
				booking.status(),
				Sputnik.timeFmtLocal(booking.bookedAt()),
				seatNames(booking, trip),
				Sputnik.timeFmtLocal(booking.paymentStartTime())
			));
		}
		return responses;
	}
	
	private BookingResponse booking(Booking booking, String paymentUrl, Map<Long, Trip> tripsById) {
		Trip trip = this.tripOf(booking, tripsById);
		return new BookingResponse(
			Sputnik.i64ToString(booking.id()), booking.bookingCode(),
			Sputnik.i64ToString(booking.tripId()),
			booking.passengerName(), booking.passengerPhone(),
			quantity(booking, trip),
			booking.unitPrice(),
			booking.totalAmount(),
			booking.status(),
			Sputnik.timeFmtLocal(booking.bookedAt()),
			seatNames(booking, trip),
			Sputnik.timeFmtLocal(booking.paymentStartTime()),
			paymentUrl
		);
	}
	
	// a page of history is mostly the same few trips, one lookup each per request
	// boy i hope this doesnt blow perf th fuck up
	private Trip tripOf(Booking booking, Map<Long, Trip> tripsById) {
		Trip trip = tripsById.get(booking.tripId());
		if (trip == null) {
			trip = this.tripService.get(booking.tripId());
			tripsById.put(trip.id(), trip);
		}
		return trip;
	}

	// phase 1 quantity thingy, the open count, or how many seats were picked
	private static int quantity(Booking booking, Trip trip) {
		if (trip.seatsType() == SeatsType.ASSIGNED_SEATING) {
			return booking.desiredAssignedSeats().popcount();
		}
		return booking.desiredOpenSeatsCount();
	}

	// names come from the trip's map at read time; after a vehicle swap bit i is the new i-th seat
	private static List<String> seatNames(Booking booking, Trip trip) {
		List<String> names = new ArrayList<>();
		if (trip.seatsType() != SeatsType.ASSIGNED_SEATING) {
			return names;
		}
		List<String> seatMap = trip.assignedSeatNames();
		for (int i = 0; i < seatMap.size(); ++i) {
			if (booking.desiredAssignedSeats().get(i)) {
				names.add(seatMap.get(i));
			}
		}
		return names;
	}
}
