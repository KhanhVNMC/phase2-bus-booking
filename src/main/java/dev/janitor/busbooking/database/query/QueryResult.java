package dev.janitor.busbooking.database.query;

import java.util.List;

/**
 * (C) 2026 gkvndev, All rights reserved.
 * Originally from Patchouli Engine (dev.gkvn.patchouli), the author's personal project.
 * Do not use, copy or redistribute outside of this project without permission.
 */
public sealed interface QueryResult<T> permits QueryResult.Empty, QueryResult.One, QueryResult.Multiple, QueryResult.Error {
	
	record Empty<T>() implements QueryResult<T> {}
	record One<T>(T value) implements QueryResult<T> {}
	record Multiple<T>(List<T> values, boolean truncated) implements QueryResult<T> {}
	record Error<T>(Exception exception) implements QueryResult<T> {}
	
	static <T> QueryResult<T> from(List<T> values, boolean truncated) {
		return switch (values.size()) {
			case 0 -> new Empty<>();
			case 1 -> {
				if (!truncated) yield new One<>(values.getFirst());
				else yield new Multiple<>(values, true);
			}
			default -> new Multiple<>(values, truncated);
		};
	}
}
