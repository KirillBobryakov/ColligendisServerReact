package com.colligendis.server.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeleteNumistaCollectionRequest {

	@NotBlank
	private String coinId;

	@NotBlank
	private String versionId;

	@NotBlank
	private String numistaCollectionItemId;
}
