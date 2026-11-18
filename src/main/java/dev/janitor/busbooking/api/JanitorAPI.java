package dev.janitor.busbooking.api;

import io.javalin.Javalin;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.util.Map;

import dev.janitor.busbooking.bootstrap.ApplicationConfig;

public class JanitorAPI {
	public void registerRoutes(JavalinConfig config) {
		config.concurrency.useVirtualThreads = true; // java 24+

		config.routes.before(ctx -> ctx.header("X-Backend-Version", ApplicationConfig.getIdentifier()));
		config.routes.get("/api/health", this::handleHealth);
	}

	public Javalin start(String host, int port) {
		Javalin app = Javalin.create(this::registerRoutes);
		return app.start(host, port);
	}

	public void handleHealth(Context ctx) {
		ctx.status(HttpStatus.OK);
		ctx.json(Map.of("status", "UP"));
	}
}
