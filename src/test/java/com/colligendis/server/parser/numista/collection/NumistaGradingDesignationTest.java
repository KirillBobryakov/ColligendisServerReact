package com.colligendis.server.parser.numista.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class NumistaGradingDesignationTest {

	@Test
	void forService_returnsAnacsDesignations() {
		List<NumistaGradingDesignation> anacs = NumistaGradingDesignation.forService(NumistaGradingService.ANACS);
		assertEquals(15, anacs.size());
		assertTrue(anacs.stream().anyMatch(d -> "844".equals(d.getNumistaValue()) && "CAMEO".equals(d.getLabel())));
	}

	@Test
	void fromNumistaValueAndService_resolvesNgcUltraCameo() {
		NumistaGradingDesignation designation = NumistaGradingDesignation
				.fromNumistaValueAndService("359", NumistaGradingService.NGC)
				.orElseThrow();
		assertEquals("ULTRA CAMEO", designation.getLabel());
	}

	@Test
	void toFormData_postsDesignationValues() {
		NumistaCollectionSaveRequest request = NumistaCollectionSaveRequest.builder()
				.coinId("216431")
				.version("536093")
				.gradingService(NumistaGradingService.NGC)
				.gradingDesignation(List.of(
						NumistaGradingDesignation.fromNumistaValueAndService("359", NumistaGradingService.NGC)
								.orElseThrow()))
				.build();

		assertEquals(List.of("359"), request.toFormData().get("gradingDesignation[]"));
	}
}
