package dev.janitor.busbooking.utils;

import java.text.Normalizer;
import java.util.Locale;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;

public class Sputnik {
	@Getter
	private static final ObjectMapper objectMapper = new ObjectMapper();

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
}
