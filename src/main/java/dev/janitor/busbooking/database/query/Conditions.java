package dev.janitor.busbooking.database.query;

import jakarta.persistence.criteria.Predicate;

/**
 * (C) 2026 gkvndev, All rights reserved.
 * Originally from Patchouli Engine (dev.gkvn.patchouli), the author's personal project.
 * Do not use, copy or redistribute outside of this project without permission.
 */
public class Conditions {
	/**
	 * Creates a condition for a column and matcher.
	 *
	 * @param column  the column to filter
	 * @param matcher the matcher used to compare column values
	 * @param <T>     the entity type
	 * @param <U>     the column value type
	 * @return the created condition
	 */
	public static <T, U> Condition<T> where(Column<T, U> column, Matcher<U> matcher) {
		return (builder, root) -> matcher.createPredicate(builder, column.toPath(root));
	}

	/**
	 * Combines conditions using logical AND.
	 *
	 * @param ands the conditions to combine
	 * @param <T>  the entity type
	 * @return the combined condition
	 */
	@SafeVarargs
	public static <T> Condition<T> and(Condition<T>... ands) {
		return (builder, root) -> {
			Predicate[] predicates = new Predicate[ands.length];
			for (int i = 0; i < ands.length; i++) {
				predicates[i] = ands[i].createPredicateCond(builder, root);
			}
			return builder.and(predicates);
		};
	}
	
	/**
	 * Combines conditions using logical OR.
	 *
	 * @param ors the conditions to combine
	 * @param <T> the entity type
	 * @return the combined condition
	 */
	@SafeVarargs
	public static <T> Condition<T> or(Condition<T>... ors) {
		return (builder, root) -> {
			Predicate[] predicates = new Predicate[ors.length];
			for (int i = 0; i < ors.length; i++) {
				predicates[i] = ors[i].createPredicateCond(builder, root);
			}
			return builder.or(predicates);
		};
	}
	
	/**
	 * Combines two conditions using logical XOR.
	 *
	 * @param left  the first condition
	 * @param right the second condition
	 * @param <T>   the entity type
	 * @return the combined condition
	 */
	public static <T> Condition<T> xor(Condition<T> left, Condition<T> right) {
		// a xor b = (a || b) && !(a && b)
		return and(or(left, right), not(and(left, right)));
	}
	
	/**
	 * Negates a condition.
	 *
	 * @param condition the condition to negate
	 * @param <T>       the entity type
	 * @return the negated condition
	 */
	public static <T> Condition<T> not(Condition<T> condition) {
		return condition.not();
	}
}