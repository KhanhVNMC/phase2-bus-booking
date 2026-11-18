package dev.janitor.busbooking.api.dto;

import java.util.Map;

public record ErrorResponse(
	String timestamp,
	int status,
	String error,
	String message,
	String path,
	Map<String, Object> details
) {}
