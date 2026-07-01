package com.colligendis.server.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateStorageLocationRequest(
		@NotBlank String name) {
}
