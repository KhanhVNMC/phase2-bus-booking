package dev.janitor.busbooking.bootstrap;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.PersistenceUnitTransactionType;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import dev.janitor.busbooking.impl.jpa.entities.BookingEntity;
import dev.janitor.busbooking.impl.jpa.entities.LocationEntity;
import dev.janitor.busbooking.impl.jpa.entities.OperatorEntity;
import dev.janitor.busbooking.impl.jpa.entities.RefundEntity;
import dev.janitor.busbooking.impl.jpa.entities.RouteEntity;
import dev.janitor.busbooking.impl.jpa.entities.TripEntity;
import dev.janitor.busbooking.impl.jpa.entities.UserEntity;
import dev.janitor.busbooking.impl.jpa.entities.VehicleEntity;

import java.util.function.Function;

import org.hibernate.cfg.JdbcSettings;
import org.hibernate.jpa.HibernatePersistenceConfiguration;

/**
 * (C) 2026 gkvndev, All rights reserved.
 * Originally from Patchouli Engine (dev.gkvn.patchouli), the author's personal project.
 * Do not use, copy or redistribute outside of this project without permission.
 */
public final class HibernateRuntime implements AutoCloseable {
	// every @Entity goes here
	private static final Class<?>[] MANAGED_ENTITIES = {
		LocationEntity.class,
		OperatorEntity.class,
		VehicleEntity.class,
		RouteEntity.class,
		TripEntity.class,
		BookingEntity.class,
		RefundEntity.class,
		UserEntity.class
	};

	private static volatile HibernateRuntime global;

	public static HibernateRuntime configureGlobal(HibernateRuntime runtime) {
		return global = runtime;
	}

	public static HibernateRuntime get() {
		HibernateRuntime current = global;
		if (current == null) {
			throw new IllegalStateException("Global HibernateRuntime has not been configured");
		}
		return current;
	}

	private final EntityManagerFactory entityManagerFactory;
	private final HikariDataSource dataSource;

	public HibernateRuntime() {
		ApplicationConfig config = ApplicationConfig.get();
		validateConfig(config);
		this.dataSource = new HikariDataSource(createPoolConfig(config));
		EntityManagerFactory entityManagerFactory = null;
		try {
			entityManagerFactory = new HibernatePersistenceConfiguration(config.getDatabaseName())
				.transactionType(PersistenceUnitTransactionType.RESOURCE_LOCAL)
				.managedClasses(MANAGED_ENTITIES)
				.property(JdbcSettings.JAKARTA_NON_JTA_DATASOURCE, this.dataSource)
				.property("hibernate.hbm2ddl.auto", config.getDatabaseSchemaMode())
				.showSql(
					config.isVerboseMode(),
					config.isVerboseMode(),
					config.isVerboseMode()
				)
			.createEntityManagerFactory();
			ensurePostgresExtensions(entityManagerFactory, config.isDatabaseInstallExtensions());
			ensurePartialIndexes(entityManagerFactory);
		} catch (RuntimeException exception) {
			if (entityManagerFactory != null) {
				entityManagerFactory.close();
			}
			this.dataSource.close();
			throw exception;
		}
		this.entityManagerFactory = entityManagerFactory;
	}

	// the location/operator/vehicle searches call unaccent() in SQL
	private static void ensurePostgresExtensions(
		EntityManagerFactory entityManagerFactory,
		boolean installExtensions
	) {
		try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
			if (!installExtensions) {
				if (!Boolean.TRUE.equals(entityManager.createNativeQuery(
					"SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'unaccent')"
				).getSingleResult())) {
					throw new IllegalStateException(
						"PostgreSQL extension unaccent is not installed"
					);
				}
				return;
			}
			EntityTransaction transaction = entityManager.getTransaction();
			try {
				transaction.begin();
				entityManager.createNativeQuery("CREATE EXTENSION IF NOT EXISTS unaccent").executeUpdate();
				transaction.commit();
			} catch (RuntimeException exception) {
				if (transaction.isActive()) {
					transaction.rollback();
				}
				throw exception;
			}
		}
	}

	// hibernate annotations cannot declare a partial index, so these are made by hand
	private static void ensurePartialIndexes(EntityManagerFactory entityManagerFactory) {
		try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
			EntityTransaction transaction = entityManager.getTransaction();
			try {
				transaction.begin();
				// one PAYMENT_HOLDING booking per user: a second concurrent hold fails its insert,
				// which rolls its seat claim back with it
				entityManager.createNativeQuery("""
					CREATE UNIQUE INDEX IF NOT EXISTS uq_bookings_one_hold_per_user
					ON bookings (user_id) WHERE status = 'PAYMENT_HOLDING'
				""").executeUpdate();
				transaction.commit();
			} catch (RuntimeException exception) {
				if (transaction.isActive()) {
					transaction.rollback();
				}
				throw exception;
			}
		}
	}

	private static HikariConfig createPoolConfig(ApplicationConfig config) {
		HikariConfig pool = new HikariConfig();
		pool.setJdbcUrl(config.getDatabaseUrl());
		pool.setUsername(config.getDatabaseUsername());
		pool.setPassword(config.getDatabasePassword());
		pool.setMinimumIdle(config.getDatabasePoolMinimumIdle());
		pool.setMaximumPoolSize(config.getDatabasePoolMaximumSize());
		pool.setConnectionTimeout(config.getDatabaseConnectionTimeoutMillis());
		pool.setValidationTimeout(config.getDatabaseValidationTimeoutMillis());
		pool.setIdleTimeout(config.getDatabaseIdleTimeoutMillis());
		pool.setMaxLifetime(config.getDatabaseMaxLifetimeMillis());
		pool.setInitializationFailTimeout(config.getDatabaseConnectionTimeoutMillis());
		pool.setPoolName(config.getDatabaseName());
		return pool;
	}

	private static void validateConfig(ApplicationConfig config) {
		if (config.getDatabasePoolMinimumIdle() < 0) {
			throw new IllegalArgumentException("DATABASE_POOL_MINIMUM_IDLE cannot be negative");
		}
		if (config.getDatabasePoolMaximumSize() < 1) {
			throw new IllegalArgumentException("DATABASE_POOL_MAXIMUM_SIZE must be positive");
		}
		if (config.getDatabasePoolMinimumIdle() > config.getDatabasePoolMaximumSize()) {
			throw new IllegalArgumentException("DATABASE_POOL_MINIMUM_IDLE cannot exceed DATABASE_POOL_MAXIMUM_SIZE");
		}
		if (config.getDatabaseConnectionTimeoutMillis() < 250) {
			throw new IllegalArgumentException("DATABASE_CONNECTION_TIMEOUT_MILLIS must be at least 250");
		}
		if (config.getDatabaseValidationTimeoutMillis() < 250) {
			throw new IllegalArgumentException("DATABASE_VALIDATION_TIMEOUT_MILLIS must be at least 250");
		}
		switch (config.getDatabaseSchemaMode()) {
			case "none", "validate", "update", "create", "create-drop" -> {}
			default -> throw new IllegalArgumentException(
				"DATABASE_SCHEMA_MODE must be none, validate, update, create, or create-drop"
			);
		}
	}

	public <T> T find(Class<T> entityClass, Object primaryKey) {
		try (var entityManager = openEntityManager()) {
			return entityManager.find(entityClass, primaryKey);
		}
	}

	public <T> T grabQuickie(Function<EntityManager, T> grab) {
		try (var entityManager = openEntityManager()) {
			return grab.apply(entityManager);
		}
	}

	// all or nothing: commits if work returns, rolls back if it throws
	public <T> T transact(Function<EntityManager, T> work) {
		try (var entityManager = openEntityManager()) {
			EntityTransaction transaction = entityManager.getTransaction();
			transaction.begin();
			try {
				T result = work.apply(entityManager);
				transaction.commit();
				return result;
			} catch (RuntimeException exception) {
				if (transaction.isActive()) {
					transaction.rollback();
				}
				throw exception;
			}
		}
	}
	
	public EntityManager openEntityManager() {
		return entityManagerFactory.createEntityManager();
	}

	@Override
	public void close() {
		if (entityManagerFactory.isOpen()) {
			entityManagerFactory.close();
		}
		if (!dataSource.isClosed()) {
			dataSource.close();
		}
	}
}
