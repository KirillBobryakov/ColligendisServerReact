package com.colligendis.server.dto;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SaveNumistaCollectionRequest {

	@NotBlank
	private String coinId;

	@NotBlank
	private String versionId;

	/** Numista collection row id when updating an existing item. */
	private String item;
	private String numistaCollectionItemId;
	private Integer quantity;
	private String grade;
	private String value;
	private String comment;
	private Boolean forSwap;
	private String swapComment;

	private String gradingService;
	private String gradingMark;
	private List<String> gradingDesignation = new ArrayList<>();
	private String gradingStrike;
	private String gradingSurface;
	private String slabNumber;
	private String cacSticker;

	private String storageLocation;
	private String storageLocationUuid;
	private String acquisitionPlace;
	private String acquisitionPlaceUuid;
	private String acquisitionDate;
	private String serialNumber;
	private String internalId;
	private String size;
}
