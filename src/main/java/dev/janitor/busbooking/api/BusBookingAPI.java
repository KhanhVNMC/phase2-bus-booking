package dev.janitor.busbooking.api;

import io.javalin.Javalin;
import io.javalin.config.JavalinConfig;

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
        // TODO
    }
    
    // where is my fucking code?????
	public Javalin start(String host, int port) {
		Javalin app = Javalin.create(this::registerRoutes);
		return app.start(host, port);
	}
}
