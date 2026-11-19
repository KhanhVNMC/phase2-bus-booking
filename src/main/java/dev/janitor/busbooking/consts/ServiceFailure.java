package dev.janitor.busbooking.consts;

// what went wrong, the api edge maps it to a status code
public enum ServiceFailure {
	INVALID_INPUT, // 400
	UNAUTHENTICATED, // 401
	FORBIDDEN, // 403
	NOT_FOUND, // 404
	CONFLICT, // 409: the rules or the current state say no
	TOO_MANY_REQUESTS, // 429
	UNAVAILABLE // 503: a dependency (cache rebuild, payment, ...) did not answer in time
}
