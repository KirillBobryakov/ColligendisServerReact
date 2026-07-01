package com.colligendis.server.parser.numista.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NumistaGradingMarkTest {

	@Test
	void fromNumistaValueAndService_resolvesNgcAncientXf() {
		NumistaGradingMark mark = NumistaGradingMark.fromNumistaValueAndService(
				"185", NumistaGradingService.NGC_ANCIENT).orElseThrow();
		assertEquals("185", mark.getNumistaValue());
		assertEquals("2", mark.getServiceId());
		assertEquals("XF", mark.getLabel());
	}

	@Test
	void toFormData_usesGradingMarkValue() {
		NumistaCollectionSaveRequest request = NumistaCollectionSaveRequest.builder()
				.coinId("216431")
				.version("536093")
				.gradingService(NumistaGradingService.NGC_ANCIENT)
				.gradingMark(NumistaGradingMark.fromNumistaValueAndService("185", NumistaGradingService.NGC_ANCIENT)
						.orElseThrow())
				.build();

		assertEquals("185", request.toFormData().getFirst("gradingMark"));
	}

	@Test
	void forService_returnsMarksForAnacs() {
		assertTrue(NumistaGradingMark.forService(NumistaGradingService.ANACS).stream()
				.anyMatch(m -> "792".equals(m.getNumistaValue()) && "PF70".equals(m.getLabel())));
	}
}
