package dev.janitor.busbooking;

import dev.janitor.busbooking.impl.auth.HmacJwtTokens;
import dev.janitor.busbooking.impl.auth.HmacVerificationCodes;
import dev.janitor.busbooking.impl.auth.Pbkdf2PasswordHasher;
import dev.janitor.busbooking.impl.cache.CacheTicketingBookingRepository;
import dev.janitor.busbooking.impl.cache.CacheTicketingTripRepository;
import dev.janitor.busbooking.impl.cache.CachedTripPublicViewRepository;
import dev.janitor.busbooking.impl.cache.TripCacheInvalidationQueue;
import dev.janitor.busbooking.impl.jpa.repos.JPABookingRepository;
import dev.janitor.busbooking.impl.jpa.repos.JPALocationRepository;
import dev.janitor.busbooking.impl.jpa.repos.JPAOperatorRepository;
import dev.janitor.busbooking.impl.jpa.repos.JPARefundRepository;
import dev.janitor.busbooking.impl.jpa.repos.JPARouteRepository;
import dev.janitor.busbooking.impl.jpa.repos.JPATripPublicViewRepository;
import dev.janitor.busbooking.impl.jpa.repos.JPATripRepository;
import dev.janitor.busbooking.impl.jpa.repos.JPAUserRepository;
import dev.janitor.busbooking.impl.jpa.repos.JPAVehicleRepository;
import dev.janitor.busbooking.impl.mail.ConsoleVerificationMailer;
import dev.janitor.busbooking.impl.payment.FakePaymentProcessor;
import dev.janitor.busbooking.impl.redis.RedisPaymentTokenStore;
import dev.janitor.busbooking.impl.redis.RedisPendingVerificationStore;
import dev.janitor.busbooking.impl.service.AuthService;
import dev.janitor.busbooking.impl.service.BookingService;
import dev.janitor.busbooking.impl.service.LocationService;
import dev.janitor.busbooking.impl.service.OperatorService;
import dev.janitor.busbooking.impl.service.RouteService;
import dev.janitor.busbooking.impl.service.TripService;
import dev.janitor.busbooking.impl.service.UserService;
import dev.janitor.busbooking.impl.service.VehicleService;
import dev.janitor.busbooking.repository.IBookingRepository;
import dev.janitor.busbooking.repository.ITripPublicViewRepository;
import dev.janitor.busbooking.repository.ITripRepository;
import dev.janitor.busbooking.api.AuthController;
import dev.janitor.busbooking.api.AuthenticationMiddleware;
import dev.janitor.busbooking.api.BookingController;
import dev.janitor.busbooking.api.BusBookingAPI;
import dev.janitor.busbooking.api.CatalogController;
import dev.janitor.busbooking.api.ResponseMapper;
import dev.janitor.busbooking.api.TripController;
import dev.janitor.busbooking.bootstrap.ApplicationConfig;
import dev.janitor.busbooking.bootstrap.HibernateRuntime;
import dev.janitor.busbooking.bootstrap.RedisRuntime;
import dev.janitor.busbooking.utils.Snowflake;
import io.javalin.Javalin;

/**
 * (C) 2026 gkvndev, All rights reserved.
 * Originally from Patchouli Engine (dev.gkvn.patchouli), the author's personal project.
 * Do not use, copy or redistribute outside of this project without permission.
 */
public class Main {
	public static void main(String[] args) throws Exception {
		long time = System.currentTimeMillis();
		if (Runtime.version().feature() < 24) {
			throw new IllegalStateException("This artifact is corrupted; it should have been built with Java 25!!");
		}
		System.out.println("[system] " + ApplicationConfig.getIdentifier() + " - Starting services and subsystems...");

		System.out.println("[system] Loading configurations...");
		ApplicationConfig config = ApplicationConfig.get();
		if (config.getServerPort() < 1 || config.getServerPort() > 65_535) {
			throw new IllegalArgumentException("SERVER_PORT must be between 1 and 65535");
		}
		if (config.getInstanceId() < 0 || config.getInstanceId() > Snowflake.MAX_INSTANCE_ID) {
			throw new IllegalArgumentException("INSTANCE_ID is required, between 0 and " + Snowflake.MAX_INSTANCE_ID);
		}
		System.out.println("[system] Javalin server bind port is: " + config.getServerPort());
		
		System.out.println("[system] Starting services...");
		Snowflake.configureGlobal(new Snowflake(config.getInstanceId()));
		HibernateRuntime hibernate = HibernateRuntime.configureGlobal(new HibernateRuntime());
		RedisRuntime redis = null;
		try {
			redis = new RedisRuntime(config);
			RedisRuntime.configureGlobal(redis);
			
			// data layer
			var locationRepo = new JPALocationRepository(hibernate);
			var operatorRepo = new JPAOperatorRepository(hibernate);
			var routeRepo = new JPARouteRepository(hibernate);
			var vehicleRepo = new JPAVehicleRepository(hibernate);
			var userRepo = new JPAUserRepository(hibernate);
			var refundRepo = new JPARefundRepository(hibernate);
			ITripRepository tripRepo = new JPATripRepository(hibernate);
			IBookingRepository bookingRepo = new JPABookingRepository(hibernate);
			ITripPublicViewRepository tripViewRepo = new JPATripPublicViewRepository(hibernate);
			TripCacheInvalidationQueue cacheInvalidation = null;
			if (config.isTripCacheEnabled()) {
				// dud
				cacheInvalidation = new TripCacheInvalidationQueue(config);
				tripRepo = new CacheTicketingTripRepository(tripRepo, cacheInvalidation);
				bookingRepo = new CacheTicketingBookingRepository(bookingRepo, cacheInvalidation);
				tripViewRepo = new CachedTripPublicViewRepository(tripViewRepo, config);
			}
			System.out.println("[system] Trip cache is " + (config.isTripCacheEnabled() ? "ON" : "OFF"));
			
			// business layer
			var locationService = new LocationService(locationRepo);
			var operatorService = new OperatorService(operatorRepo);
			var routeService = new RouteService(routeRepo, locationRepo);
			var vehicleService = new VehicleService(vehicleRepo, operatorRepo);
			var tripService = new TripService(tripRepo, tripViewRepo, routeRepo, vehicleRepo, config);
			var bookingService = new BookingService(
				bookingRepo, tripRepo, userRepo, refundRepo, new RedisPaymentTokenStore(), new FakePaymentProcessor(config), config
			);
			var authService = new AuthService(
				userRepo,
				new RedisPendingVerificationStore(),
				new HmacVerificationCodes(config.getEmailOtpSecret()),
				new ConsoleVerificationMailer(),
				new Pbkdf2PasswordHasher(config.getPasswordHashIterations()),
				new HmacJwtTokens(config.getJwtSecret(), config.getJwtIssuer(), config.getJwtTtlSeconds()),
				config
			);
			var userService = new UserService(userRepo);
			boolean superAdminCreated = authService.bootstrapSuperAdmin(
				config.getBootstrapSuperAdminEmail(), config.getBootstrapSuperAdminPassword(),
				config.getBootstrapSuperAdminFullName(), config.getBootstrapSuperAdminPhone()
			);
			if (superAdminCreated) {
				System.out.println("[system] Created the first SUPER_ADMIN: " + config.getBootstrapSuperAdminEmail());
			}
			var mapper = new ResponseMapper(locationService, operatorService, tripService);
			// i give up, do it yourself
			BusBookingAPI api = new BusBookingAPI(
				new AuthenticationMiddleware(authService),
				new AuthController(authService, userService),
				new TripController(tripService),
				new BookingController(bookingService, mapper),
				new CatalogController(locationService, operatorService, routeService, vehicleService, mapper)
			);
			Javalin application = api.start(config.getServerHost(), config.getServerPort());
			try {
				registerShutdownHook(application, cacheInvalidation, redis, hibernate);
			} catch (RuntimeException e) {
				application.stop();
				throw e;
			}
			System.out.println("[system] Ready! Subsystem and services started in " + (System.currentTimeMillis() - time) + "ms");
		} catch (Exception e) {
			if (redis != null) { redis.close(); }
			hibernate.close();
			throw e;
		}
	}
	
	static void registerShutdownHook(
		Javalin application, TripCacheInvalidationQueue cacheInvalidation, RedisRuntime redis, HibernateRuntime hibernate
	) {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			application.stop();
			// last flush
			if (cacheInvalidation != null) {
				cacheInvalidation.close();
			}
			redis.close();
			hibernate.close();
		}, "busbooker-shutdown"));
	}
}
