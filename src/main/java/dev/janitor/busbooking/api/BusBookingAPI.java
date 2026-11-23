package dev.janitor.busbooking.api;

// PRESENTATION LAYER entry point
public class BusBookingAPI {
    public BusBookingAPI(
        AuthenticationMiddleware authentication, AuthController authController,
        TripController tripController, BookingController bookingController
    ) {}
}
