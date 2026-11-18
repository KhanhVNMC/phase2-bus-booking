package dev.janitor.busbooking.database.query;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * (C) 2026 gkvndev, All rights reserved.
 * Originally from Patchouli Engine (dev.gkvn.patchouli), the author's personal project.
 * Do not use, copy or redistribute outside of this project without permission.
 */
public interface ColMatcher<T> {
	Predicate createPredicate(CriteriaBuilder cit, Root<T> root);
	
	/**
	 * Creates a condition comparing two columns for equality.
	 *
	 * @param left  the first column
	 * @param right the second column
	 * @param <T>   the entity type
	 * @param <U>   the column value type
	 * @return the created condition
	 */
	public static <T, U> Condition<T> equ(Column<T, U> left, Column<T, U> right) {
		return (cit, root) -> cit.equal(
			left.toPath(root), right.toPath(root)
		);
	}
	
	/**
	 * Creates a condition that compares two string columns 
	 * for equality (case insensitive)
	 *
	 * @param left  the first column
	 * @param right the second column
	 * @param <T>   the entity type
	 * @return the created condition
	 */
	public static <T> Condition<T> equIgnoreCase(Column<T, String> left, Column<T, String> right) {
		return (cit, root) -> cit.equal(
			cit.lower(left.toPath(root)),
			cit.lower(right.toPath(root))
		);
	}
	
	/**
	 * Creates a condition comparing two columns using greater than.
	 *
	 * @param left  the first column
	 * @param right the second column
	 * @param <T>   the entity type
	 * @param <U>   the column value type
	 * @return the created condition
	 */
	public static <T, U extends Comparable<? super U>> 
	Condition<T> gt(Column<T, U> left, Column<T, U> right) {
		return (cit, root) -> cit.greaterThan(
			left.toPath(root), right.toPath(root)
		);
	}
	
	/**
	 * Creates a condition comparing two columns using greater than or equal.
	 *
	 * @param left  the first column
	 * @param right the second column
	 * @param <T>   the entity type
	 * @param <U>   the column value type
	 * @return the created condition
	 */
	public static <T, U extends Comparable<? super U>> 
	Condition<T> gte(Column<T, U> left, Column<T, U> right) {
		return (cit, root) -> cit.greaterThanOrEqualTo(
			left.toPath(root), right.toPath(root)
		);
	}
	
	/**
	 * Creates a condition comparing two columns using less than.
	 *
	 * @param left  the first column
	 * @param right the second column
	 * @param <T>   the entity type
	 * @param <U>   the column value type
	 * @return the created condition
	 */
	public static <T, U extends Comparable<? super U>> 
	Condition<T> lt(Column<T, U> left, Column<T, U> right) {
		return (cit, root) -> cit.lessThan(
			left.toPath(root), right.toPath(root)
		);
	}
	
	/**
	 * Creates a condition comparing two columns using less than or equal.
	 *
	 * @param left  the first column
	 * @param right the second column
	 * @param <T>   the entity type
	 * @param <U>   the column value type
	 * @return the created condition
	 */
	public static <T, U extends Comparable<? super U>> 
	Condition<T> lte(Column<T, U> left, Column<T, U> right) {
		return (cit, root) -> cit.lessThanOrEqualTo(
			left.toPath(root), right.toPath(root)
		);
	}
}
