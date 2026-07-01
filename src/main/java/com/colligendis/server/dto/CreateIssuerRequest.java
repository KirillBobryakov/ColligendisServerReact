package com.colligendis.server.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateIssuerRequest(
		@NotBlank String numistaCode,
		@NotBlank String name,
		String countryNumistaCode,
		String subjectNumistaCode) {
}
