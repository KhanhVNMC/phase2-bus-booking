package dev.janitor.busbooking.utils;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.janitor.busbooking.service.ServiceException;
import lombok.Getter;

public class Sputnik {
	@Getter
	private static final ObjectMapper objectMapper = new ObjectMapper();
	static {
		// records only: helpers like SeatSet.isEmpty() must not become an "empty" field
		objectMapper.configure(MapperFeature.AUTO_DETECT_IS_GETTERS, false);
		// a value cached by an older build still reads after a deploy
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}
	
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
	// vietnamese mobile/landline after +84 -> 0: 0 then 9 or 10 digits
	private static final Pattern PHONE_PATTERN = Pattern.compile("0[0-9]{9,10}");

	public static String unaccent(String value) {
		if (value == null) {
			return null;
		}
		return Normalizer.normalize(value, Normalizer.Form.NFD)
			.replaceAll("\\p{M}+", "")
			.replace('đ', 'd')
			.replace('Đ', 'D')
		;
	}
	
	public static String containing(String searchText) {
		String escapedText = Sputnik.toLower(Sputnik.unaccent(searchText.trim()));
		escapedText = escapedText.replace("!", "!!");
		escapedText = escapedText.replace("%", "!%");
		escapedText = escapedText.replace("_", "!_");
		return "%" + escapedText + "%";
	}

	public static String toLower(String s) {
		return s.toLowerCase(Locale.ROOT);
	}
	
	// trimmed, null when null or blank
	public static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
	
	// trimmed, 1..maxLength chars, no control characters
	public static String requireText(String value, String fieldName, int maxLength) {
		String trimmed = trimToNull(value);
		if (trimmed == null) {
			throw ServiceException.invalid(fieldName + " is required");
		}
		if (trimmed.length() > maxLength) {
			throw ServiceException.invalid(fieldName + " must be at most " + maxLength + " characters");
		}
		for (int i = 0; i < trimmed.length(); ++i) {
			if (Character.isISOControl(trimmed.charAt(i))) {
				throw ServiceException.invalid(fieldName + " must not contain control characters");
			}
		}
		return trimmed;
	}
	
	// trimmed + lowercased, at most 100 chars
	public static String requireEmail(String value, String fieldName) {
		String email = requireText(value, fieldName, 100);
		email = toLower(email);
		if (!EMAIL_PATTERN.matcher(email).matches()) {
			throw ServiceException.invalid(fieldName + " is not a valid email");
		}
		return email;
	}
	
	// trimmed, spaces/dots/dashes dropped, +84 -> 0
	public static String requirePhone(String value, String fieldName) {
		String phone = requireText(value, fieldName, 20);
		phone = phone.replace(" ", "");
		phone = phone.replace(".", "");
		phone = phone.replace("-", "");
		if (phone.startsWith("+84")) {
			phone = "0" + phone.substring(3);
		}
		if (!PHONE_PATTERN.matcher(phone).matches()) {
			throw ServiceException.invalid(fieldName + " is not a valid vietnamese phone number");
		}
		return phone;
	}
	
	public static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
	public static String timeFmtLocal(long epochMs) {
		if (epochMs == 0) {
			return null;
		}
		return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), VIETNAM).format(DATE_TIME);
	}
	
	public static String i64ToString(long value) {
		return String.valueOf(value);
	}
	
	public static int requireRange(Integer value, String fieldName, int min, int max) {
		if (value == null) {
			throw ServiceException.invalid(fieldName + " is required");
		}
		if (value < min || value > max) {
			throw ServiceException.invalid(fieldName + " must be between " + min + " and " + max);
		}
		return value;
	}

    public static Long parseTime(String value, String fieldName) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDateTime.parse(value.trim(), DATE_TIME).atZone(VIETNAM).toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            throw ServiceException.invalid(fieldName + " must look like 2026-12-31T08:30:00");
        }
    }

    public static long[] dayWindow(String date, String fieldName) {
        if (date == null || date.isBlank()) {
            return null;
        }
        long dayStart;
        try {
            dayStart = LocalDate.parse(date.trim()).atStartOfDay(VIETNAM).toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            throw ServiceException.invalid(fieldName + " must look like 2026-12-31");
        }
        return new long[] { dayStart, dayStart + (24L * 60L * 60L * 1000L) };
    }
} 
