package dev.janitor.busbooking;

import dev.janitor.busbooking.api.JanitorAPI;
import dev.janitor.busbooking.bootstrap.ApplicationConfig;
import dev.janitor.busbooking.bootstrap.HibernateRuntime;
import dev.janitor.busbooking.bootstrap.RedisRuntime;
import dev.janitor.busbooking.utils.Snowflake;
import io.javalin.Javalin;

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

			Javalin application = new JanitorAPI().start(config.getServerHost(), config.getServerPort());
			try {
				registerShutdownHook(application, redis, hibernate);
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

	private static void registerShutdownHook(Javalin application, RedisRuntime redis, HibernateRuntime hibernate) {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			application.stop();
			redis.close();
			hibernate.close();
		}, "janitor-shutdown"));
	}
}
