package com.colligendis.server.parser.numista.collection;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.util.StringUtils;

import lombok.Builder;
import lombok.Data;

/**
 * Parsed HTML row returned by Numista after saving a collection item.
 */
@Data
@Builder
public class NumistaCollectionSaveResponse {

	/** Numista collection item nid (onclick arg 4 / {@code data-thumb-id}). */
	private String numistaCollectionItemId;
	private String numistaThumbUserId;
	/** {@link com.colligendis.server.database.numista.model.NType} nid (onclick arg 2). */
	private String coinId;
	/** {@link com.colligendis.server.database.numista.model.Variant} nid (onclick arg 3). */
	private String versionId;

	private Integer quantity;
	/** Grade code from the edit button ({@code sup}, etc.). */
	private String gradeCode;
	/** Human-readable grade shown in the row ({@code XF}, etc.). */
	private String displayGrade;

	private String value;
	private Boolean forSwap;
	private String comment;
	private String swapComment;

	@Builder.Default
	private List<String> pictures = new ArrayList<>();

	/** Numista {@code gradingService} code ({@code 1} = NGC, etc.). */
	private String gradingService;
	/** Numista {@code gradingMark} code ({@code collec_form_grading_mark}). */
	private String gradingMark;
	private String gradingDesignationJson;
	private String gradingStrike;
	private String gradingSurface;

	private String slabNumber;
	private String cacSticker;
	private String storageLocation;
	private String acquisitionPlace;
	private String acquisitionDate;
	private String serialNumber;
	private String internalId;
	private String size;

	private String slabDisplay;
	private String measureDisplay;
	private String priceDisplay;
	private String quantityDisplay;

	/** Raw {@code <tr>} fragment returned by Numista. */
	private String responseRowHtml;

	public NumistaGradingService resolveGradingService() {
		return NumistaGradingService.fromNumistaValue(gradingService).orElse(null);
	}

	public NumistaGradingMark resolveGradingMark() {
		NumistaGradingService service = resolveGradingService();
		if (service != null) {
			return NumistaGradingMark.fromNumistaValueAndService(gradingMark, service).orElse(null);
		}
		return NumistaGradingMark.fromNumistaValue(gradingMark).orElse(null);
	}

	/**
	 * Parses designation ids from {@link #gradingDesignationJson} when present
	 * ({@code gradingDesignation} array in the edit-button JSON).
	 */
	public List<NumistaGradingDesignation> resolveGradingDesignations() {
		if (!StringUtils.hasText(gradingDesignationJson)) {
			return List.of();
		}
		NumistaGradingService service = resolveGradingService();
		Matcher matcher = Pattern.compile("\"gradingDesignation\"\\s*:\\s*\\[([^\\]]*)]")
				.matcher(gradingDesignationJson);
		if (!matcher.find()) {
			return List.of();
		}
		List<NumistaGradingDesignation> result = new ArrayList<>();
		Matcher ids = Pattern.compile("\"(\\d+)\"").matcher(matcher.group(1));
		while (ids.find()) {
			String id = ids.group(1);
			if (service != null) {
				NumistaGradingDesignation.fromNumistaValueAndService(id, service).ifPresent(result::add);
			} else {
				NumistaGradingDesignation.fromNumistaValue(id).ifPresent(result::add);
			}
		}
		return result;
	}
}
