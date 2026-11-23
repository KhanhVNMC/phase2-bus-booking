package dev.janitor.busbooking.api;

import java.util.ArrayList;
import java.util.List;

import dev.janitor.busbooking.api.dto.TripRequest;
import dev.janitor.busbooking.consts.TripStatus;
import dev.janitor.busbooking.model.Trip;
import dev.janitor.busbooking.service.ITripService;
import dev.janitor.busbooking.utils.Sputnik;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;

public class TripController {
    private final ITripService tripService;

    // public trip reads (cached by redis) and the admin trip lifecycle
    public TripController(ITripService tripService) {
        this.tripService = tripService;
    }

    public void registerRoutes(JavalinDefaultRoutingApi router) {
        router.get("/api/trips", this::search);
        router.get("/api/trips/{tripId}", this::get);

        router.get("/api/admin/trips", this::adminList);
        router.post("/api/admin/trips", this::create);
        router.put("/api/admin/trips/{tripId}", this::update);
        router.delete("/api/admin/trips/{tripId}", this::cancel);
    }

    // ?originId=&destinationId=&departureDate=yyyy-MM-dd, all optional like phase 1
    public void search(Context ctx) {
        long[] window = Sputnik.dayWindow(ctx.queryParam("departureDate"), "departureDate");
        ctx.json(ResponseMapper.trips(tripService.searchPublic(
            APIUtils.queryId(ctx, "originId"),
            APIUtils.queryId(ctx, "destinationId"),
            window == null ? null : window[0],
            window == null ? null : window[1]
        )));
    }

    public void get(Context ctx) {
        ctx.json(ResponseMapper.trip(tripService.getPublic(APIUtils.pathId(ctx, "tripId"))));
    }

    // Which trips match comes from the database; they are rendered through the views like the public side,
    // admin edits pass through the cache right away, so this is never stale on them
    public void adminList(Context ctx) {
        APIUtils.admin(ctx);
        long[] window = Sputnik.dayWindow(ctx.queryParam("departureDate"), "departureDate");
        List<Trip> trips = tripService.adminSearch(
            APIUtils.queryId(ctx, "routeId"),
            APIUtils.queryEnum(ctx, "status", TripStatus.class),
            window == null ? null : window[0],
            window == null ? null : window[1]
        );
        List<Long> tripIds = new ArrayList<>();
        for (Trip trip : trips) {
            tripIds.add(trip.id());
        }
        ctx.json(ResponseMapper.trips(tripService.getPublicMany(tripIds)));
    }

    public void create(Context ctx) {
        APIUtils.admin(ctx);
        TripRequest request = APIUtils.body(ctx, TripRequest.class);
        Trip trip = tripService.create(
            APIUtils.parseId(request.routeId(), "routeId"),
            APIUtils.parseId(request.vehicleId(), "vehicleId"),
            Sputnik.parseTime(request.departureTime(), "departureTime"),
            Sputnik.parseTime(request.arrivalTime(), "arrivalTime"),
            request.price(),
            request.seatsType(),
            request.totalSeats(),
            request.maskedSeatNames(),
            request.bookingCutoffMinutes()
        );
        ctx.status(HttpStatus.CREATED);
        ctx.json(ResponseMapper.trip(tripService.getPublic(trip.id())));
    }

    public void update(Context ctx) {
        APIUtils.admin(ctx);
        long tripId = APIUtils.pathId(ctx, "tripId");
        TripRequest request = APIUtils.body(ctx, TripRequest.class);
        tripService.update(
            tripId,
            APIUtils.parseId(request.routeId(), "routeId"),
            APIUtils.parseId(request.vehicleId(), "vehicleId"),
            Sputnik.parseTime(request.departureTime(), "departureTime"),
            Sputnik.parseTime(request.arrivalTime(), "arrivalTime"),
            request.price(),
            request.seatsType(), request.totalSeats(),
            request.maskedSeatNames(),
            request.bookingCutoffMinutes()
        );
        ctx.json(ResponseMapper.trip(tripService.getPublic(tripId)));
    }

    // P1 DELETE is a cancel, the trip stays (as CANCELLED) for history and refunds
    public void cancel(Context ctx) {
        APIUtils.admin(ctx);
        long tripId = APIUtils.pathId(ctx, "tripId");
        tripService.cancel(tripId);
        ctx.json(ResponseMapper.trip(tripService.getPublic(tripId)));
    }
}
