package com.colligendis.server.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateAcquisitionPlaceRequest(
		@NotBlank String name) {
}
