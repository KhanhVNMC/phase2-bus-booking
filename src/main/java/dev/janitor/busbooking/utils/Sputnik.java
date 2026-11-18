package dev.janitor.busbooking.utils;

import java.text.Normalizer;
import java.util.Locale;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;

/**
 * (C) 2026 gkvndev, All rights reserved.
 * Originally from Patchouli Engine (dev.gkvn.patchouli), the author's personal project.
 * Do not use, copy or redistribute outside of this project without permission.
 */
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

	public static String toLower(String s) {
		return s.toLowerCase(Locale.ROOT);
	}
}
