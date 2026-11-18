package dev.janitor.busbooking.database.query;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;

import java.util.function.Function;

/**
 * (C) 2026 gkvndev, All rights reserved.
 * Originally from Patchouli Engine (dev.gkvn.patchouli), the author's personal project.
 * Do not use, copy or redistribute outside of this project without permission.
 */
public final class Column<T, U> {
	private final Function<Root<T>, Expression<U>> pathBuilder;

	public Column(Function<Root<T>, Expression<U>> pathBuilder ) {
		this.pathBuilder = pathBuilder ;
	}

	public Expression<U> toPath(Root<T> root) {
		return pathBuilder.apply(root);
	}

	public static <T, U> Column<T, U> of(String attributeName) {
		return new Column<>(root -> root.get(attributeName));
	}
}
