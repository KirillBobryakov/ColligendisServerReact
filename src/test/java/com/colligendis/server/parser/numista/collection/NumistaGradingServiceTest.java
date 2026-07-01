package com.colligendis.server.parser.numista.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NumistaGradingServiceTest {

	@Test
	void fromNumistaValue_resolvesKnownServices() {
		assertEquals(NumistaGradingService.NGC, NumistaGradingService.fromNumistaValue("1").orElseThrow());
		assertEquals(NumistaGradingService.ANACS, NumistaGradingService.fromNumistaValue("8").orElseThrow());
		assertEquals(NumistaGradingService.ICG, NumistaGradingService.fromNumistaValue("10").orElseThrow());
	}

	@Test
	void toFormData_usesNumistaValue() {
		NumistaCollectionSaveRequest request = NumistaCollectionSaveRequest.builder()
				.coinId("216431")
				.version("536093")
				.gradingService(NumistaGradingService.NGC)
				.build();

		assertEquals("1", request.toFormData().getFirst("gradingService"));
	}

	@Test
	void fromLabel_isCaseInsensitive() {
		assertTrue(NumistaGradingService.fromLabel("pcgs").isPresent());
		assertEquals(NumistaGradingService.PCGS, NumistaGradingService.fromLabel("PCGS").orElseThrow());
	}
}
