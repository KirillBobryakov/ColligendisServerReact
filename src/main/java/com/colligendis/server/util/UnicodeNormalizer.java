package com.colligendis.server.util;

import java.text.Normalizer;
import java.util.Locale;

public class UnicodeNormalizer {
	public static String normalize(String input) {
		if (input == null) {
			return null;
		}

		// Normalize unicode characters
		String normalized = Normalizer.normalize(input, Normalizer.Form.NFKD);

		// Remove diacritical marks
		normalized = normalized.replaceAll("\\p{M}", "");

		// Special replacements not handled by NFKD
		normalized = normalized
				.replace("ß", "ss")
				.replace("ẞ", "SS")
				.replace("æ", "ae")
				.replace("Æ", "AE")
				.replace("ø", "o")
				.replace("Ø", "O")
				.replace("đ", "d")
				.replace("Đ", "D")
				.replace("ł", "l")
				.replace("Ł", "L");

		return normalized.toLowerCase(Locale.ROOT);
	}

}
