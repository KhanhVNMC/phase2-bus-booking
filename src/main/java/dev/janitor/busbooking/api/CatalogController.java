package dev.janitor.busbooking.api;

import java.util.ArrayList;
import java.util.List;

import dev.janitor.busbooking.api.dto.CreateOperatorRequest;
import dev.janitor.busbooking.api.dto.CreateRouteRequest;
import dev.janitor.busbooking.api.dto.CreateVehicleRequest;
import dev.janitor.busbooking.api.dto.LocationRequest;
import dev.janitor.busbooking.api.dto.LocationResponse;
import dev.janitor.busbooking.api.dto.OperatorRequest;
import dev.janitor.busbooking.api.dto.OperatorResponse;
import dev.janitor.busbooking.api.dto.RouteRequest;
import dev.janitor.busbooking.api.dto.RouteResponse;
import dev.janitor.busbooking.api.dto.VehicleRequest;
import dev.janitor.busbooking.api.dto.VehicleResponse;
import dev.janitor.busbooking.consts.ActiveStatus;
import dev.janitor.busbooking.consts.VehicleStatus;
import dev.janitor.busbooking.model.Location;
import dev.janitor.busbooking.model.Operator;
import dev.janitor.busbooking.model.Route;
import dev.janitor.busbooking.model.Vehicle;
import dev.janitor.busbooking.service.ILocationService;
import dev.janitor.busbooking.service.IOperatorService;
import dev.janitor.busbooking.service.IRouteService;
import dev.janitor.busbooking.service.IVehicleService;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;

// the admin catalog + the one public list (locations)
public class CatalogController {
    private final ILocationService locationService;
    private final IVehicleService vehicleService;
    private final IOperatorService operatorService;
    private final IRouteService routeService; // TODO
    private final ResponseMapper mapper;

    public CatalogController(
        ILocationService locationService, IOperatorService operatorService, IRouteService routeService,
        IVehicleService vehicleService, ResponseMapper mapper
    ) {
        this.locationService = locationService;
        this.operatorService = operatorService;
        this.routeService = routeService;
        this.vehicleService = vehicleService;
        this.mapper = mapper;
    }

    public void registerRoutes(JavalinDefaultRoutingApi router) {
        router.get("/api/locations", this::listLocations);
        router.get("/api/admin/locations", this::adminListLocations);
        router.post("/api/admin/locations", this::createLocation);
        router.put("/api/admin/locations/{locationId}", this::updateLocation);
        router.get("/api/admin/vehicles", this::listVehicles);
        router.post("/api/admin/vehicles", this::createVehicle);
        router.put("/api/admin/vehicles/{vehicleId}", this::updateVehicle);
    }

    // PUBLIC. The search box on the booking page
    public void listLocations(Context ctx) {
        ctx.json(locations(locationService.search(ctx.queryParam("q"))));
    }

    public void adminListLocations(Context ctx) {
        APIUtils.admin(ctx);
        ctx.json(locations(locationService.search(ctx.queryParam("q"))));
    }

    public void createLocation(Context ctx) {
        APIUtils.admin(ctx);
        LocationRequest request = APIUtils.body(ctx, LocationRequest.class);
        ctx.status(HttpStatus.CREATED);
        ctx.json(ResponseMapper.location(locationService.create(
            request.name(), request.province(), request.address())
        ));
    }

    public void updateLocation(Context ctx) {
        APIUtils.admin(ctx);
        long locationId = APIUtils.pathId(ctx, "locationId");
        LocationRequest request = APIUtils.body(ctx, LocationRequest.class);
        ctx.json(ResponseMapper.location(locationService.update(
            locationId, request.name(), request.province(), request.address())
        ));
    }

    private List<LocationResponse> locations(List<Location> locations) {
        List<LocationResponse> responses = new ArrayList<>();
        for (Location location : locations) {
            responses.add(ResponseMapper.location(location));
        }
        return responses;
    }

    public void listVehicles(Context ctx) {
        APIUtils.admin(ctx);
        Long operatorId = APIUtils.queryId(ctx, "operatorId");
        VehicleStatus status = APIUtils.queryEnum(ctx, "status", VehicleStatus.class);
        List<VehicleResponse> responses = new ArrayList<>();
        for (Vehicle vehicle : vehicleService.search(ctx.queryParam("q"), operatorId, status)) {
            responses.add(mapper.vehicle(vehicle));
        }
        ctx.json(responses);
    }

    public void createVehicle(Context ctx) {
        APIUtils.admin(ctx);
        CreateVehicleRequest request = APIUtils.body(ctx, CreateVehicleRequest.class);
        Vehicle vehicle = vehicleService.create(
            APIUtils.parseId(request.operatorId(), "operatorId"),
            request.licensePlate(),
            request.vehicleType(),
            request.seatCapacity(),
            request.seatLabels()
        );
        ctx.status(HttpStatus.CREATED);
        ctx.json(mapper.vehicle(vehicle));
    }

    /* Cap nhat phuong tien */
    public void updateVehicle(Context ctx) {
        APIUtils.admin(ctx);
        long vehicleId = APIUtils.pathId(ctx, "vehicleId");
        VehicleRequest request = APIUtils.body(ctx, VehicleRequest.class);
        Vehicle vehicle = vehicleService.update(
            vehicleId,
            APIUtils.parseId(request.operatorId(), "operatorId"),
            request.licensePlate(),
            request.vehicleType(),
            request.seatCapacity(),
            request.seatLabels(),
            request.status()
        );
        ctx.json(mapper.vehicle(vehicle));
    }

    // Hien thi cac nha xe voi trang thai hoat dong request yeu cau
    public void listOperators(Context ctx) {
        APIUtils.admin(ctx);
        ActiveStatus status = APIUtils.queryEnum(ctx, "status", ActiveStatus.class);
        List<OperatorResponse> responses = new ArrayList<>();
        // Tim tat ca cac nha xe yeu cau roi tra ve
        for (Operator operator : operatorService.search(ctx.queryParam("q"), status)) {
            responses.add(ResponseMapper.operator(operator));
        }
        ctx.json(responses);
    }

    // Tao nha xe (chi admin moi dc lam)
    public void createOperator(Context ctx) {
        APIUtils.admin(ctx);
        CreateOperatorRequest request = APIUtils.body(ctx, CreateOperatorRequest.class);
        ctx.status(HttpStatus.CREATED);
        ctx.json(ResponseMapper.operator(operatorService.create(
        	request.name(), request.address(), 
        	request.phone(), request.email()
        )));
    }

    // cap nhat thong tin nha xe, chi admin, giong pha 1
    public void updateOperator(Context ctx) {
        APIUtils.admin(ctx);
        long operatorId = APIUtils.pathId(ctx, "operatorId");
        OperatorRequest request = APIUtils.body(ctx, OperatorRequest.class);
        ctx.json(ResponseMapper.operator(operatorService.update(
            operatorId, request.name(), request.address(), request.phone(), request.email(), request.status()
        )));
    }

    // hien thi danh sach cac chang voi active status tu reqest
    public void listRoutes(Context ctx) {
        APIUtils.admin(ctx);
        Long originId = APIUtils.queryId(ctx, "originId");
        Long destinationId = APIUtils.queryId(ctx, "destinationId");
        ActiveStatus status = APIUtils.queryEnum(ctx, "status", ActiveStatus.class);
        List<RouteResponse> responses = new ArrayList<>();
        for (Route route : routeService.search(originId, destinationId, status)) {
            responses.add(mapper.route(route));
        }
        ctx.json(responses);
    }

    // khoi tao chang (admin only)
    public void createRoute(Context ctx) {
        APIUtils.admin(ctx);
        CreateRouteRequest request = APIUtils.body(ctx, CreateRouteRequest.class);
        Route route = routeService.create(
            APIUtils.parseId(request.originId(), "originId"),
            APIUtils.parseId(request.destinationId(), "destinationId"),
            request.estimatedDurationMinutes()
        );
        ctx.status(HttpStatus.CREATED);
        ctx.json(mapper.route(route));
    }

    // cap nhat chang (admin only)
    public void updateRoute(Context ctx) {
        APIUtils.admin(ctx);
        long routeId = APIUtils.pathId(ctx, "routeId");
        RouteRequest request = APIUtils.body(ctx, RouteRequest.class);
        Route route = routeService.update(
            routeId,
            APIUtils.parseId(request.originId(), "originId"),
            APIUtils.parseId(request.destinationId(), "destinationId"),
            request.estimatedDurationMinutes(),
            request.status()
        );
        ctx.json(mapper.route(route));
    }
}
