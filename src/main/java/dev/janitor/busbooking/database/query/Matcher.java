package dev.janitor.busbooking.database.query;

import java.util.Collection;

import dev.janitor.busbooking.utils.Sputnik;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

/**
 * (C) 2026 gkvndev, All rights reserved.
 * Originally from Patchouli Engine (dev.gkvn.patchouli), the author's personal project.
 * Do not use, copy or redistribute outside of this project without permission.
 */
@FunctionalInterface
public interface Matcher<T> {
	Predicate createPredicate(CriteriaBuilder cit, Expression<T> expr);
	
	static <T> Matcher<T> isIn(Collection<T> values) {
		return (cit, expr) -> {
			if (values == null || values.isEmpty()) {
				return cit.disjunction();
			}
			return expr.in(values);
		};
	}
	
	static Matcher<String> startsWith(String prefix) {
		return (cit, expr) -> cit.like(expr, 
			escapeLike(prefix) + "%", '\\'
		);
	}
	
	static Matcher<String> endsWith(String suffix) {
		return (cit, expr) -> cit.like(expr, 
			"%" + escapeLike(suffix), '\\'
		);
	}
	
	static Matcher<String> endsWithIgnoreCase(String suffix) {
		return (cit, expr) -> cit.like(
			cit.lower(expr), 
			"%" + escapeLike(Sputnik.toLower(suffix)), 
			'\\'
		);
	}
	
	static Matcher<String> endsWithIgnoreCaseAndMarkings(String suffix) {
		return (cit, expr) -> cit.like(
			cit.lower(cit.function("unaccent", String.class, expr)), 
			"%" + escapeLike(Sputnik.toLower(Sputnik.unaccent(suffix))), 
			'\\'
		);
	}
	
	static Matcher<String> contains(String text) {
		return (cit, expr) -> cit.like(expr, 
			"%" + escapeLike(text) + "%", '\\'
		);
	}
	
	static Matcher<String> containsIgnoreCase(String text) {
		return (cit, expr) -> cit.like(
			cit.lower(expr),
			"%" + escapeLike(Sputnik.toLower(text)) + "%",
			'\\'
		);
	}
	
	static Matcher<String> regex(String pattern) {
		return (cit, expr) -> {
			return cit.isTrue(cit.function(
				"regexp_like", Boolean.class, 
				expr, cit.literal(pattern)
			));
		};
	}
	
	static <T> Matcher<T> equ(T value) {
		return (cit, expr) -> value == null 
			? cit.isNull(expr) 
			: cit.equal(expr, value)
		;
	}
	
	static Matcher<String> equIgnoreCase(String value) {
		return (cit, expr) -> value == null
			? cit.isNull(expr)
			: cit.equal(cit.lower(expr), Sputnik.toLower(value))
		;
	}
	
	static <T> Matcher<T> nil() {
		return (cit, expr) -> cit.isNull(expr);
	}
	
	static <T> Matcher<T> notNil() {
		return (cit, expr) -> cit.isNotNull(expr);
	}
	
	static <T extends Comparable<? super T>> 
	Matcher<T> gt(T value) {
		return (cit, expr) -> cit.greaterThan(expr, value);
	}
	
	static <T extends Comparable<? super T>> 
	Matcher<T> gte(T value) {
		return (cit, expr) -> cit.greaterThanOrEqualTo(expr, value);
	}
	
	static <T extends Comparable<? super T>> 
	Matcher<T> lt(T value) {
		return (cit, expression) -> cit.lessThan(expression, value);
	}
	
	static <T extends Comparable<? super T>> 
	Matcher<T> lte(T value) {
		return (cit, expression) -> cit.lessThanOrEqualTo(expression, value);
	}
	
	private static String escapeLike(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}
}
