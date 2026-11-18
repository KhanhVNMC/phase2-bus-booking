package dev.janitor.busbooking.database.query;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * (C) 2026 gkvndev, All rights reserved.
 * Originally from Patchouli Engine (dev.gkvn.patchouli), the author's personal project.
 * Do not use, copy or redistribute outside of this project without permission.
 */
@FunctionalInterface
public interface Condition<T> {
	Predicate createPredicateCond(CriteriaBuilder cit, Root<T> root);
	
	default Condition<T> and(Condition<T> other) {
		return (cit, root) -> cit.and(
			this.createPredicateCond(cit, root), 
			other.createPredicateCond(cit, root)
		);
	}
	
	default Condition<T> or(Condition<T> other) {
		return (cit, root) -> cit.or(
			this.createPredicateCond(cit, root), 
			other.createPredicateCond(cit, root)
		);
	}
	
	default Condition<T> not() {
		return (cit, root) -> cit.not(this.createPredicateCond(cit, root));
	}
}
