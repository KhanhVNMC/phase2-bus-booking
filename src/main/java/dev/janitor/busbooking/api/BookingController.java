package dev.janitor.busbooking.api;

import dev.janitor.busbooking.api.dto.BookingRequest;
import dev.janitor.busbooking.consts.BookingStatus;
import dev.janitor.busbooking.model.Booking;
import dev.janitor.busbooking.service.IAuthService.Principal;
import dev.janitor.busbooking.service.IBookingService;
import dev.janitor.busbooking.service.IBookingService.PaymentHold;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;

// bookings for the logged in user, plus the admin list
public class BookingController {
	public static final String PAY_PAGE_PREFIX = "/fake-gateway/pay/";// infinite monet generator
	
	private final IBookingService bookingService;
	private final ResponseMapper mapper;
	
	public BookingController(IBookingService bookingService, ResponseMapper mapper) {
		this.bookingService = bookingService;
		this.mapper = mapper;
	}
	
	public void registerRoutes(JavalinDefaultRoutingApi router) {
		router.post("/api/bookings", this::create);
		router.get("/api/bookings/{bookingId}", this::get);
		router.delete("/api/bookings/{bookingId}", this::cancel);
		router.get("/api/users/me/bookings", this::history);
		router.get("/api/admin/bookings", this::adminList);
	}
	
	// by default it is a hold + the gateway link
	// to get the old phase 1 behaviour, add ?facade either ret CONFIRMED or 409
	public void create(Context ctx) {
		Principal principal = APIUtils.principal(ctx);
		BookingRequest request = APIUtils.body(ctx, BookingRequest.class);
		long tripId = APIUtils.parseId(request.tripId(), "tripId");
		ctx.status(HttpStatus.CREATED);
		// pay immediately, infinite money hack
		if (ctx.queryParam("facade") != null) {
			Booking booking = this.bookingService.holdAndPay(
				principal.userId(), tripId, request.passengerName(), request.passengerPhone(), request.quantity(), request.seats()
			);
			ctx.json(this.mapper.booking(booking, null));
			return;
		}
		// keep the payment
		PaymentHold hold = this.bookingService.hold(
			principal.userId(), tripId, request.passengerName(), request.passengerPhone(), request.quantity(), request.seats()
		);
		ctx.json(this.mapper.booking(hold.booking(), PAY_PAGE_PREFIX + hold.paymentToken()));
	}
	
	public void get(Context ctx) {
		Principal principal = APIUtils.principal(ctx);
		Booking booking = this.bookingService.get(principal.userId(), principal.isAdmin(), APIUtils.pathId(ctx, "bookingId"));
		ctx.json(this.mapper.booking(booking, null));
	}
	
	// phase 1 DELETE is a cancel, a hold is dropped, a paid one cancelled and
	// put in in the refund queue right away, piles up and issuing over time
	public void cancel(Context ctx) {
		Principal principal = APIUtils.principal(ctx);
		Booking booking = this.bookingService.cancel(principal.userId(), principal.isAdmin(), APIUtils.pathId(ctx, "bookingId"));
		ctx.json(this.mapper.booking(booking, null));
	}
	
	public void history(Context ctx) {
		Principal principal = APIUtils.principal(ctx);
		ctx.json(this.mapper.history(this.bookingService.history(principal.userId())));
	}
	
	public void adminList(Context ctx) {
		APIUtils.admin(ctx);
		ctx.json(this.mapper.bookings(this.bookingService.adminSearch(
			APIUtils.queryId(ctx, "userId"),
			APIUtils.queryId(ctx, "tripId"),
			APIUtils.queryEnum(ctx, "status", BookingStatus.class)
		)));
	}
}
