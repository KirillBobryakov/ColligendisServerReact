package com.colligendis.server.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DenominationNumericFilterTest {

	@Test
	void numericText_usesIntegerStringForWholeNumbers() {
		assertEquals("10", DenominationNumericFilter.numericText(10.0));
		assertEquals("10", DenominationNumericFilter.numericText(10d));
	}

	@Test
	void numericText_preservesFractionalValues() {
		assertEquals("0.5", DenominationNumericFilter.numericText(0.5));
	}
}
