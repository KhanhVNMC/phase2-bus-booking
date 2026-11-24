package dev.janitor.busbooking.api;

import java.time.LocalDateTime;

import dev.janitor.busbooking.api.dto.ErrorResponse;
import dev.janitor.busbooking.consts.ServiceFailure;
import dev.janitor.busbooking.service.ServiceException;
import dev.janitor.busbooking.utils.Sputnik;
import io.javalin.Javalin;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

// PRESENTATION LAYER entry point
public class BusBookingAPI {
    private final AuthenticationMiddleware authentication;
    private final AuthController authController;
    private final CatalogController catalogController;
    private final TripController tripController;
    private final BookingController bookingController;

    public BusBookingAPI(
        AuthenticationMiddleware authentication, AuthController authController,
        TripController tripController, BookingController bookingController, CatalogController catalogController
    ) {
        this.authentication = authentication;
        this.authController = authController;
        this.catalogController = catalogController;
        this.tripController = tripController;
        this.bookingController = bookingController;
    }

    public void registerRoutes(JavalinConfig config) {
        config.concurrency.useVirtualThreads = true; // java 24+
        config.routes.before("/api/*", this.authentication.handler());
        config.routes.get("/api/health", this::handleHealth);
        config.routes.exception(Exception.class, (e, ctx) -> {
            System.err.println("ERROR: " + ctx.method() + " " + ctx.path() + " failed:");
            e.printStackTrace();
            this.writeError(ctx, HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error");
        });
        config.routes.error(404, ctx -> {
            String contentType = ctx.res().getContentType();
            if (contentType == null || !contentType.contains("json")) {
                this.writeError(ctx, HttpStatus.NOT_FOUND, "Endpoint not found");
            }
        });
        config.routes.exception(ServiceException.class, (e, ctx) -> {
            this.writeError(ctx, createStatusFrom(e.failure), e.getMessage());
        });
        this.authController.registerRoutes(config.routes);
        this.catalogController.registerRoutes(config.routes);
        this.tripController.registerRoutes(config.routes);
        this.bookingController.registerRoutes(config.routes);
    }

    public void handleHealth(Context ctx) {
        ctx.status(HttpStatus.OK);
        ctx.json(java.util.Map.of("status", "UP"));
    }

    public Javalin start(String host, int port) {
        Javalin app = Javalin.create(this::registerRoutes);
        return app.start(host, port);
    }

    public void writeError(Context ctx, HttpStatus status, String message) {
        ctx.status(status);
        ctx.json(new ErrorResponse(LocalDateTime.now(Sputnik.VIETNAM).withNano(0).toString(), status.getCode(), status.getMessage(), message, ctx.path(), null));
    }

    public static HttpStatus createStatusFrom(ServiceFailure failure) {
        switch (failure) {
            case INVALID_INPUT: return HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED: return HttpStatus.UNAUTHORIZED;
            case FORBIDDEN: return HttpStatus.FORBIDDEN;
            case NOT_FOUND: return HttpStatus.NOT_FOUND;
            case CONFLICT: return HttpStatus.CONFLICT;
            case TOO_MANY_REQUESTS: return HttpStatus.TOO_MANY_REQUESTS;
            case UNAVAILABLE: return HttpStatus.SERVICE_UNAVAILABLE;
            default: return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
}
