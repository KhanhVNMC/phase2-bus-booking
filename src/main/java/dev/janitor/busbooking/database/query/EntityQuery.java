package dev.janitor.busbooking.database.query;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

import dev.janitor.busbooking.bootstrap.HibernateRuntime;
import dev.janitor.busbooking.contracts.DatabaseEntityObject;
import dev.janitor.busbooking.utils.Promise;

// very cursed: "HACK" generic to preserve type for method chaining
/**
 * (C) 2026 gkvndev, All rights reserved.
 * Originally from Patchouli Engine (dev.gkvn.patchouli), the author's personal project.
 * Do not use, copy or redistribute outside of this project without permission.
 */
@SuppressWarnings("unchecked")
public abstract class EntityQuery<T extends DatabaseEntityObject, HACK extends EntityQuery<T, HACK>> {
	private final HibernateRuntime runtime;
	
	private final Class<T> entityType;  // target for the query
	private final List<Condition<T>> conditions = new ArrayList<>();
	private final List<SortOrdering<T>> orderings = new ArrayList<>();
	private int limit = 50; // <= 0 means unlimited
	
	@Getter private EntityManager providedEntityManager;
	
	protected EntityQuery(Class<T> entityType, HibernateRuntime runtime) {
		this.entityType = entityType;
		this.runtime = runtime;
	}
	
	/**
	 * Sets the entity manager used to execute this query.
	 *
	 * @param providedEntityManager the entity manager to use, or {@code null} to
	 *                              create one automatically
	 * @return this query instance
	 */
	public HACK setEntityManager(EntityManager providedEntityManager) {
		this.providedEntityManager = providedEntityManager; // null for it to auto
		return (HACK) this;
	}
	
	/**
	 * Adds a column-based condition to this query.
	 *
	 * @param targetColumn the column to filter
	 * @param matcher      the matcher used to compare column values
	 * @param <U>          the column value type
	 * @return this query instance
	 */
	public <U> HACK where(Column<T, U> targetColumn, Matcher<U> matcher) {
		return this.where(Conditions.where(targetColumn, matcher));
	}
	
	/**
	 * Adds a complete boolean condition to this query.
	 * 
	 * @param condition the condition to add
	 * @return this query instance
	 */
	public HACK where(Condition<T> condition) {
		this.conditions.add(condition);
		return (HACK) this;
	}
	
	/**
	 * Adds a complete boolean condition to this query.
	 * 
	 * @param condition the condition to add
	 * @return this query instance
	 */
	public HACK cond(Condition<T> condition) {
		return this.where(condition);
	}
	
	/**
	 * Adds a sorting rule to this query, stackable.
	 *
	 * @param targetColumn the column to sort by
	 * @param sort         the sort direction
	 * @return this query instance
	 */
	public HACK orderBy(Column<T, ?> targetColumn, Sort sort) {
		this.orderings.add(new SortOrdering<>(targetColumn, sort));
		return (HACK) this;
	}
	
	/**
	 * Sets the maximum number of results returned by the query.
	 *
	 * @param limit the maximum number of results
	 * @return this query instance
	 * @throws IllegalArgumentException if the limit is less than 1
	 */
	public HACK limit(int limit) {
		if (limit < 1) {
			throw new IllegalArgumentException("limit must be at least 1");
		}
		this.limit = limit;
		return (HACK) this;
	}
	
	/**
	 * Removes the result limit, allowing all matching rows to be returned.
	 *
	 * @return this query instance
	 */
	public HACK unlimited() {
		this.limit = 0;
		return (HACK) this;
	}
	
	/**
	 * Executes this query asynchronously and blocks the 
	 * current thread until completion.
	 *
	 * @return a promise containing the query result
	 */
	public QueryResult<T> awaitFetch() throws RuntimeException { return dispatch(false).await(); }
	
	/**
	 * Executes this query asynchronously, converting any execution 
	 * exception into a {@link QueryResult.Error} and blocks the 
	 * current thread until completion.
	 *
	 * @return a promise containing the query result
	 */
	public QueryResult<T> awaitSafeFetch() throws RuntimeException { return dispatch(true).await(); }
	
	/**
	 * Executes this query asynchronously.
	 *
	 * @return a promise containing the query result
	 */
	public Promise<QueryResult<T>> fetchAsync() throws RuntimeException { return dispatch(false); }
	
	/**
	 * Executes this query asynchronously, converting any execution 
	 * exception into a {@link QueryResult.Error}.
	 *
	 * @return a promise containing the query result
	 */
	public Promise<QueryResult<T>> safeFetchAsync() { return dispatch(true); }

	/**
	 * Counts all values matching the current conditions and 
	 * blocks the current thread until completion.
	 * 
	 * <p>The query limit and sorting rules are ignored.
	 *
	 * @return a promise containing the number of matching values
	 */
	public long awaitCount() throws RuntimeException { return count().await(); }
	
	/**
	 * Counts all values matching the current conditions asynchronously.
	 *
	 * <p>The query limit and sorting rules are ignored.
	 *
	 * @return a promise containing the number of matching values
	 */
	public Promise<Long> count() {
		// capture for the promise to run asynchronously
		final var finalConditions = List.copyOf(this.conditions);
		
		Promise<Long> promise = new Promise<>();
		runtime.queryExecutor().execute(() -> {
			try {
				long result;
				if (providedEntityManager != null && providedEntityManager.isOpen()) {
					result = this.executeCount(providedEntityManager, finalConditions);
				} else {
					try (var entityManager = HibernateRuntime.get().openEntityManager()) {
						result = this.executeCount(entityManager, finalConditions);
					}
				}
				promise.fulfill(result);
			} catch (Exception e) {
				promise.reject(e);
			}
		});
		return promise;
	}
	
	private long executeCount(
		EntityManager entityManager, List<Condition<T>> conditions
	) {
		CriteriaBuilder builder = entityManager.getCriteriaBuilder();
		CriteriaQuery<Long> criteria = builder.createQuery(Long.class);
		Root<T> root = criteria.from(entityType);
		
		criteria.select(builder.count(root));
			
		// predicates/conditions
		if (!conditions.isEmpty()) {
			Predicate[] predicates = new Predicate[conditions.size()];
			for (int i = 0; i < conditions.size(); ++i) {
				predicates[i] = conditions.get(i).createPredicateCond(builder, root);
			}
			criteria.where(builder.and(predicates));
		}
		
		return entityManager.createQuery(criteria).getSingleResult();
	}
			
	// internal implementation
	private Promise<QueryResult<T>> dispatch(boolean safely) {
		// capture for the promise to run asynchronously
		final int finalLimit = this.limit;
		final var finalConditions = List.copyOf(this.conditions);
		final var finalOrderings = List.copyOf(this.orderings);
		
		Promise<QueryResult<T>> promise = new Promise<>();
		runtime.queryExecutor().execute(() -> {
			try {
				QueryResult<T> result;
				if (providedEntityManager != null && providedEntityManager.isOpen()) {
					result = this.executeQuery(providedEntityManager, 
						finalLimit, finalConditions, finalOrderings
					);
				} else {
					try (var entityManager = HibernateRuntime.get().openEntityManager()) {
						result = this.executeQuery(entityManager,
							finalLimit, finalConditions, finalOrderings
						);
					}
				}
				promise.fulfill(result);
			} catch (Exception e) {
				if (!safely) {
					promise.reject(e);
					return;
				}
				promise.fulfill(new QueryResult.Error<T>(e));
			}
		});
		return promise;
	}
	
	private final QueryResult<T> executeQuery(
		EntityManager entityManager,
		int requestedLimit, 
		List<Condition<T>> conditions,
		List<SortOrdering<T>> sorts
	) {
		CriteriaBuilder builder = entityManager.getCriteriaBuilder();
		CriteriaQuery<T> criteria = builder.createQuery(entityType);
		Root<T> root = criteria.from(entityType);
		
		// select the entity to do stuff on
		criteria.select(root);
		
		// predicates/conditions
		if (!conditions.isEmpty()) {
			Predicate[] predicates = new Predicate[conditions.size()];
			for (int i = 0; i < conditions.size(); ++i) {
				predicates[i] = conditions.get(i).createPredicateCond(builder, root);
			}
			criteria.where(builder.and(predicates));
		}
			
		// sorting
		if (!sorts.isEmpty()) {
			List<Order> orders = new ArrayList<>(sorts.size());
			for (SortOrdering<T> ordering : sorts) {
				orders.add(ordering.toOrder(builder, root));
			}
			criteria.orderBy(orders);
		}
		
		TypedQuery<T> query = entityManager.createQuery(criteria);
		// if the query has a ceiling
		if (requestedLimit > 0) {
			// request 1 extra to see if the list is truncated
			query.setMaxResults(requestedLimit + 1);
			
			List<T> values = query.getResultList();
			boolean truncated = values.size() > requestedLimit;
			if (truncated) {
				values.removeLast(); // keep it within the requested size
			}
			
			return QueryResult.from(values, truncated);
		}
			// unlimited
		return QueryResult.from(query.getResultList(), false);
	}
	
	private record SortOrdering<T>(Column<T, ?> targetColumn, Sort sort) {
		Order toOrder(CriteriaBuilder cit, Root<T> root) {
			Expression<?> expression = targetColumn.toPath(root);
			return sort == Sort.ASCEND ? cit.asc(expression) : cit.desc(expression);
		}
	}
}
