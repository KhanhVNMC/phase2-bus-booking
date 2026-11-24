package dev.janitor.busbooking.bootstrap;

import lombok.Getter;
import lombok.Setter;

/**
 * (C) 2026 gkvndev, All rights reserved.
 * Originally from Patchouli Engine (dev.gkvn.patchouli), the author's personal project.
 * Do not use, copy or redistribute outside of this project without permission.
 */
@Getter @Setter
public class ApplicationConfig {
	public static final String ENGINE_FRIENDLY_NAME = "Janitor";
	public static final String ENGINE_VERSION = "1.0.0";

	private static volatile ApplicationConfig global = new ApplicationConfig();

	public static ApplicationConfig get() {
		return global;
	}

	public static void setGlobal(ApplicationConfig config) {
		global = config;
	}

	public static String getIdentifier() {
		return ENGINE_FRIENDLY_NAME + " v" + ENGINE_VERSION;
	}

	// SYSTEM
	private boolean debugMode = envBoolean("DEBUG_MODE", false);
	private boolean verboseMode = envBoolean("VERBOSE_MODE", false);
	private String serverHost = env("SERVER_HOST", "0.0.0.0");
	private int serverPort = envInt("SERVER_PORT", 8080);
	private int instanceId = envInt("INSTANCE_ID", 1); // required, no default

	// POSTGRES
	private String databaseName = env("DATABASE_NAME", "busbooking");
	private String databaseUrl = env("DATABASE_URL", "jdbc:postgresql://localhost:5432") + "/" + databaseName;
	private String databaseUsername = env("DATABASE_USERNAME", "postgres");
	private String databasePassword = env("DATABASE_PASSWORD", "123");
	private String databaseSchemaMode = env("DATABASE_SCHEMA_MODE", "update");
	private boolean databaseInstallExtensions = envBoolean("DATABASE_INSTALL_EXTENSIONS", true);
	private int databasePoolMinimumIdle = envInt("DATABASE_POOL_MINIMUM_IDLE", 2);
	private int databasePoolMaximumSize = envInt("DATABASE_POOL_MAXIMUM_SIZE", 16);
	private int databaseConnectionTimeoutMs = envInt("DATABASE_CONNECTION_TIMEOUT_MS", 10_000);
	private int databaseValidationTimeoutMs = envInt("DATABASE_VALIDATION_TIMEOUT_MS", 5_000);
	private int databaseIdleTimeoutMs = envInt("DATABASE_IDLE_TIMEOUT_MS", 10 * 60_000);
	private int databaseMaxLifetimeMs = envInt("DATABASE_MAX_LIFETIME_MS", 30 * 60_000);

	// REDIS
	private String redisUrl = env("REDIS_URL", "");
	private String redisHost = env("REDIS_HOST", "localhost");
	private int redisPort = envInt("REDIS_PORT", 6379);
	private int redisConnectionTimeoutMs = envInt("REDIS_CONNECTION_TIMEOUT_MS", 5_000);
	private int redisSocketTimeoutMs = envInt("REDIS_SOCKET_TIMEOUT_MS", 5_000);
	private int redisPoolMinimumIdle = envInt("REDIS_POOL_MINIMUM_IDLE", 1);
	private int redisPoolMaximumIdle = envInt("REDIS_POOL_MAXIMUM_IDLE", 8);
	private int redisPoolMaximumTotal = envInt("REDIS_POOL_MAXIMUM_TOTAL", 16);

	// SEATS
	private int minSellableSeats = envInt("MIN_SELLABLE_SEATS", 1);
	private int maxSeatsPerBooking = envInt("MAX_SEATS_PER_BOOKING", 6); // worst case: family of 6

	// BOOKING CUTOFF (per trip, frozen at creation)
	private int defaultBookingCutoffMinutes = envInt("DEFAULT_BOOKING_CUTOFF_MINUTES", 15);
	private int minBookingCutoffMinutes = envInt("MIN_BOOKING_CUTOFF_MINUTES", 10);

	// BOOKING HOLDS
	private int paymentHoldTtlSeconds = envInt("PAYMENT_HOLD_TTL_SECONDS", 10 * 60);
	private int holdSweepIntervalSeconds = envInt("HOLD_SWEEP_INTERVAL_SECONDS", 30);
	private int holdSweepBatchSize = envInt("HOLD_SWEEP_BATCH_SIZE", 500);

	// TRIP CACHE
	private boolean tripCacheEnabled = envBoolean("TRIP_CACHE_ENABLED", true); // false = straight to the db (benchmark baseline)
	private int tripCacheLeaseTtlMs = envInt("TRIP_CACHE_LEASE_TTL_MS", 2_000); // a dead rebuilder frees its trips after this
	private int tripCacheWaitTimeoutMs = envInt("TRIP_CACHE_WAIT_TIMEOUT_MS", 2_000); // then 503
	private int tripCacheWaitPollMs = envInt("TRIP_CACHE_WAIT_POLL_MS", 50);
	private int tripCacheTtlSeconds = envInt("TRIP_CACHE_TTL_SECONDS", 60); // backstop only
	private int tripCacheFlushWindowMs = envInt("TRIP_CACHE_FLUSH_WINDOW_MS", 500);
	private int tripCacheFlushBatchSize = envInt("TRIP_CACHE_FLUSH_BATCH_SIZE", 1_000);
	private int tripCachePremiumSpinMs = envInt("TRIP_CACHE_PREMIUM_SPIN_MS", 500); // then demoted to an economy ticket

	// AUTH
	private String jwtSecret = env("JWT_SECRET", "");
	private String jwtIssuer = env("JWT_ISSUER", "busbooking");
	private int jwtTtlSeconds = envInt("JWT_TTL_SECONDS", 60 * 60);
	private String emailOtpSecret = env("EMAIL_OTP_SECRET", "");
	private int passwordHashIterations = envInt("PASSWORD_HASH_ITERATIONS", 210_000); //
	private String bootstrapSuperAdminEmail = env("BOOTSTRAP_SUPER_ADMIN_EMAIL", "");
	private String bootstrapSuperAdminPassword = env("BOOTSTRAP_SUPER_ADMIN_PASSWORD", "");
	private String bootstrapSuperAdminFullName = env("BOOTSTRAP_SUPER_ADMIN_FULL_NAME", "Super Admin");
	private String bootstrapSuperAdminPhone = env("BOOTSTRAP_SUPER_ADMIN_PHONE", "0900000000");

	// EMAIL VERIFICATION
	private int verificationCodeTtlSeconds = envInt("VERIFICATION_CODE_TTL_SECONDS", 10 * 60);
	private int verificationMaxAttempts = envInt("VERIFICATION_MAX_ATTEMPTS", 5);
	private int verificationResendCooldownSeconds = envInt("VERIFICATION_RESEND_COOLDOWN_SECONDS", 60);
	private int mailMaxRetries = envInt("MAIL_MAX_RETRIES", 3);

	// SMTP
	private String smtpHost = env("SMTP_HOST", "localhost");
	private int smtpPort = envInt("SMTP_PORT", 1025); // mailpit
	private String smtpFrom = env("SMTP_FROM", "no-reply@busbooking.local");

	// FAKE PAYMENT
	private boolean paymentBenchmarkMode = envBoolean("PAYMENT_BENCHMARK_MODE", false); // always succeed, no delay
	private int paymentMinDelayMs = envInt("PAYMENT_MIN_DELAY_MS", 200);
	private int paymentMaxDelayMs = envInt("PAYMENT_MAX_DELAY_MS", 1_500);
	private int paymentDeclinePercent = envInt("PAYMENT_DECLINE_PERCENT", 7);
	private int paymentTimeoutPercent = envInt("PAYMENT_TIMEOUT_PERCENT", 3);
	private int paymentTimeoutMs = envInt("PAYMENT_TIMEOUT_MS", 5_000);

	// FAKE BANK REFUNDS (one at a time, retried with backoff, FAILED for a human after max attempts)
	private int refundIssueIntervalMs = envInt("REFUND_ISSUE_INTERVAL_MS", 2_000);
	private int refundMinDelayMs = envInt("REFUND_MIN_DELAY_MS", 1_000);
	private int refundMaxDelayMs = envInt("REFUND_MAX_DELAY_MS", 3_000);
	private int refundFailurePercent = envInt("REFUND_FAILURE_PERCENT", 5);
	private int refundMaxAttempts = envInt("REFUND_MAX_ATTEMPTS", 5);
	private int refundFirstRetrySeconds = envInt("REFUND_FIRST_RETRY_SECONDS", 30); // x4 every attempt

	private static String env(String name, String fallback) {
		String value = verboseGetEnv(name);
		return value == null || value.isBlank() ? fallback : value.trim();
	}

	private static int envInt(String name, int fallback) {
		String value = verboseGetEnv(name);
		if (value == null || value.isBlank()) {
			return fallback;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(name + " must be an integer", e);
		}
	}

	private static boolean envBoolean(String name, boolean fallback) {
		String value = verboseGetEnv(name);
		if (value == null || value.isBlank()) {
			return fallback;
		}
		if (value.trim().equalsIgnoreCase("true")) {
			return true;
		}
		if (value.trim().equalsIgnoreCase("false")) {
			return false;
		}
		throw new IllegalArgumentException(name + " must be true or false");
	}

	public static String verboseGetEnv(String name) {
		String env = System.getenv(name);
		System.out.printf("[env-config] %s = %s\n", name, (env == null ? "DEFAULT" : env));
		return env;
	}
}
