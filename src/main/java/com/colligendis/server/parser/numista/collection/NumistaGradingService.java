package com.colligendis.server.parser.numista.collection;

import java.util.Arrays;
import java.util.Optional;

import org.springframework.util.StringUtils;

/**
 * Values for Numista collection form {@code collec_form_grading_service} / {@code gradingService}.
 */
public enum NumistaGradingService {

	NGC("1", "NGC"),
	NGC_ANCIENT("2", "NGC Ancient"),
	PCGS("3", "PCGS"),
	PMG("4", "PMG"),
	CAC("5", "CAC"),
	PCGS_BANKNOTE("6", "PCGS Banknote"),
	NGCX("7", "NGCX"),
	ANACS("8", "ANACS"),
	GENI("9", "GENI"),
	ICG("10", "ICG");

	private final String numistaValue;
	private final String label;

	NumistaGradingService(String numistaValue, String label) {
		this.numistaValue = numistaValue;
		this.label = label;
	}

	public String getNumistaValue() {
		return numistaValue;
	}

	public String getLabel() {
		return label;
	}

	public static Optional<NumistaGradingService> fromNumistaValue(String value) {
		if (!StringUtils.hasText(value)) {
			return Optional.empty();
		}
		String trimmed = value.trim();
		return Arrays.stream(values())
				.filter(s -> s.numistaValue.equals(trimmed))
				.findFirst();
	}

	public static Optional<NumistaGradingService> fromLabel(String label) {
		if (!StringUtils.hasText(label)) {
			return Optional.empty();
		}
		String trimmed = label.trim();
		return Arrays.stream(values())
				.filter(s -> s.label.equalsIgnoreCase(trimmed))
				.findFirst();
	}
}
