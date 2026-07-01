package com.colligendis.server.parser.numista.collection;

import java.util.ArrayList;
import java.util.List;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import lombok.Builder;
import lombok.Data;

/**
 * Form body for {@code POST https://en.numista.com/vous/save_collection.php}
 * ({@code application/x-www-form-urlencoded}).
 */
@Data
@Builder
public class NumistaCollectionSaveRequest {

	public static final String SAVE_URL = "https://en.numista.com/vous/save_collection.php";

	/** Numista type id ({@code coinId}). */
	private String coinId;

	/** Numista variant id ({@code version}). */
	private String version;

	/**
	 * Existing collection item id when editing; omit or leave blank for a new item.
	 */
	private String item;

	private Integer quantity;
	private String grade;
	private String value;
	private String comment;

	@Builder.Default
	private Boolean forSwap = false;

	private String swapComment;

	@Builder.Default
	private List<String> pictures = new ArrayList<>();

	/** {@code collec_form_grading_service} — use {@link NumistaGradingService}. */
	private NumistaGradingService gradingService;
	/** {@code collec_form_grading_mark} — use {@link NumistaGradingMark}. */
	private NumistaGradingMark gradingMark;

	/** {@code collec_form_grading_designation} — options depend on {@link #gradingService}. */
	@Builder.Default
	private List<NumistaGradingDesignation> gradingDesignation = new ArrayList<>();

	private String gradingStrike;
	private String gradingSurface;
	private String slabNumber;
	private String cacSticker;
	/** Free-text or new location name ({@code storageLocation} form field). */
	private String storageLocation;
	/** Existing {@link com.colligendis.server.database.numista.model.StorageLocation} uuid for this user. */
	private String storageLocationUuid;
	/** Resolved location node; linked on {@link com.colligendis.server.database.numista.model.NumistaCollectionItem} via {@code STORAGE_IN}. */
	private com.colligendis.server.database.numista.model.StorageLocation linkedStorageLocation;
	/** Free-text or new place name ({@code collec_form_acquisition_place}). */
	private String acquisitionPlace;
	/** Existing {@link com.colligendis.server.database.numista.model.AcquisitionPlace} uuid for this user. */
	private String acquisitionPlaceUuid;
	/** Resolved place node; linked on {@link com.colligendis.server.database.numista.model.NumistaCollectionItem} via {@code ACQUISITION_IN}. */
	private com.colligendis.server.database.numista.model.AcquisitionPlace linkedAcquisitionPlace;
	private String acquisitionDate;
	private String serialNumber;
	private String internalId;
	private String size;

	public MultiValueMap<String, String> toFormData() {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		add(form, "coinId", coinId);
		add(form, "version", version);
		add(form, "item", item);
		if (quantity != null) {
			add(form, "quantity", String.valueOf(quantity));
		}
		add(form, "grade", grade);
		add(form, "value", value);
		add(form, "comment", comment);
		if (forSwap != null) {
			add(form, "forSwap", forSwap ? "1" : "0");
		}
		add(form, "swapComment", swapComment);
		for (String picture : pictures) {
			if (StringUtils.hasText(picture)) {
				form.add("pictures[]", picture);
			}
		}
		if (gradingService != null) {
			add(form, "gradingService", gradingService.getNumistaValue());
		}
		if (gradingMark != null && !"0".equals(gradingMark.getNumistaValue())) {
			add(form, "gradingMark", gradingMark.getNumistaValue());
		}
		for (NumistaGradingDesignation designation : gradingDesignation) {
			if (designation != null && StringUtils.hasText(designation.getNumistaValue())) {
				form.add("gradingDesignation[]", designation.getNumistaValue());
			}
		}
		add(form, "gradingStrike", gradingStrike);
		add(form, "gradingSurface", gradingSurface);
		add(form, "slabNumber", slabNumber);
		add(form, "cacSticker", cacSticker);
		add(form, "storageLocation", storageLocation);
		add(form, "acquisitionPlace", acquisitionPlace);
		add(form, "acquisitionDate", acquisitionDate);
		add(form, "serialNumber", serialNumber);
		add(form, "internalId", internalId);
		add(form, "size", size);
		return form;
	}

	private static void add(MultiValueMap<String, String> form, String key, String value) {
		if (StringUtils.hasText(value)) {
			form.add(key, value);
		}
	}
}
