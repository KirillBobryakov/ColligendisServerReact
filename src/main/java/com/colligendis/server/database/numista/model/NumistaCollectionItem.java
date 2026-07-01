package com.colligendis.server.database.numista.model;

import java.util.ArrayList;
import java.util.List;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.parser.numista.collection.NumistaCollectionSaveRequest;
import com.colligendis.server.parser.numista.collection.NumistaCollectionSaveResponse;
import com.colligendis.server.parser.numista.collection.NumistaGradingDesignation;
import com.colligendis.server.util.UnicodeNormalizer;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class NumistaCollectionItem extends AbstractNode {

	public static final String LABEL = "NUMISTA_COLLECTION_ITEM";

	public static final String FOR_NTYPE = "FOR_NTYPE";
	private NType nType;

	public static final String FOR_VARIANT = "FOR_VARIANT";
	private Variant variant;

	public static final String ACQUISITION_IN = "ACQUISITION_IN";
	private AcquisitionPlace acquisitionPlace;

	public static final String STORAGE_IN = "STORAGE_IN";
	private StorageLocation storageLocation;

	/** Numista collection row id ({@code data-thumb-id} / edit button). */
	private String numistaCollectionItemId;

	private String numistaThumbUserId;
	private String coinId;
	private String versionId;

	private Integer quantity;
	private String grade;
	private String value;
	private String comment;
	private String normalizedComment;
	private Boolean forSwap;
	private String swapComment;
	private String normalizedSwapComment;

	private List<String> pictures = new ArrayList<>();

	private String gradingService;
	private String gradingMark;
	private List<String> gradingDesignation = new ArrayList<>();
	private String gradingStrike;
	private String gradingSurface;
	private String gradingDesignationJson;

	private String slabNumber;
	private String cacSticker;
	private String acquisitionDate;
	private String serialNumber;
	private String internalId;
	private String size;

	private String displayGrade;
	private String slabDisplay;
	private String measureDisplay;
	private String priceDisplay;
	private String quantityDisplay;
	private String responseRowHtml;

	public static NumistaCollectionItem fromRequestAndResponse(
			NumistaCollectionSaveRequest request,
			NumistaCollectionSaveResponse response) {
		NumistaCollectionItem item = new NumistaCollectionItem();

		if (request != null) {
			if (org.springframework.util.StringUtils.hasText(request.getItem())) {
				item.setNumistaCollectionItemId(request.getItem());
			}
			item.setCoinId(request.getCoinId());
			item.setVersionId(request.getVersion());
			item.setQuantity(request.getQuantity());
			item.setGrade(request.getGrade());
			item.setValue(request.getValue());
			item.setComment(request.getComment());
			item.setForSwap(request.getForSwap());
			item.setSwapComment(request.getSwapComment());
			if (request.getPictures() != null) {
				item.setPictures(new ArrayList<>(request.getPictures()));
			}
			if (request.getGradingService() != null) {
				item.setGradingService(request.getGradingService().getNumistaValue());
			}
			if (request.getGradingMark() != null && !"0".equals(request.getGradingMark().getNumistaValue())) {
				item.setGradingMark(request.getGradingMark().getNumistaValue());
			}
			if (request.getGradingDesignation() != null) {
				item.setGradingDesignation(request.getGradingDesignation().stream()
						.filter(d -> d != null)
						.map(NumistaGradingDesignation::getNumistaValue)
						.collect(java.util.stream.Collectors.toCollection(ArrayList::new)));
			}
			item.setGradingStrike(request.getGradingStrike());
			item.setGradingSurface(request.getGradingSurface());
			item.setSlabNumber(request.getSlabNumber());
			item.setCacSticker(request.getCacSticker());
			if (request.getLinkedStorageLocation() != null) {
				item.setStorageLocation(request.getLinkedStorageLocation());
			}
			if (request.getLinkedAcquisitionPlace() != null) {
				item.setAcquisitionPlace(request.getLinkedAcquisitionPlace());
			}
			item.setAcquisitionDate(request.getAcquisitionDate());
			item.setSerialNumber(request.getSerialNumber());
			item.setInternalId(request.getInternalId());
			item.setSize(request.getSize());
		}

		if (response != null) {
			if (response.getNumistaCollectionItemId() != null) {
				item.setNumistaCollectionItemId(response.getNumistaCollectionItemId());
			}
			item.setNumistaThumbUserId(response.getNumistaThumbUserId());
			if (response.getCoinId() != null) {
				item.setCoinId(response.getCoinId());
			}
			if (response.getVersionId() != null) {
				item.setVersionId(response.getVersionId());
			}
			if (response.getQuantity() != null) {
				item.setQuantity(response.getQuantity());
			}
			if (response.getGradeCode() != null) {
				item.setGrade(response.getGradeCode());
			}
			if (response.getValue() != null) {
				item.setValue(response.getValue());
			}
			if (response.getComment() != null) {
				item.setComment(response.getComment());
			}
			if (response.getForSwap() != null) {
				item.setForSwap(response.getForSwap());
			}
			if (response.getSwapComment() != null) {
				item.setSwapComment(response.getSwapComment());
			}
			if (response.getPictures() != null && !response.getPictures().isEmpty()) {
				item.setPictures(new ArrayList<>(response.getPictures()));
			}
			if (response.getGradingService() != null) {
				item.setGradingService(response.getGradingService());
			}
			if (response.getGradingMark() != null) {
				item.setGradingMark(response.getGradingMark());
			}
			if (response.getGradingDesignationJson() != null) {
				item.setGradingDesignationJson(response.getGradingDesignationJson());
			}
			if (response.getGradingStrike() != null) {
				item.setGradingStrike(response.getGradingStrike());
			}
			if (response.getGradingSurface() != null) {
				item.setGradingSurface(response.getGradingSurface());
			}
			if (response.getSlabNumber() != null) {
				item.setSlabNumber(response.getSlabNumber());
			}
			if (response.getCacSticker() != null) {
				item.setCacSticker(response.getCacSticker());
			}
			if (response.getAcquisitionDate() != null) {
				item.setAcquisitionDate(response.getAcquisitionDate());
			}
			if (response.getSerialNumber() != null) {
				item.setSerialNumber(response.getSerialNumber());
			}
			if (response.getInternalId() != null) {
				item.setInternalId(response.getInternalId());
			}
			if (response.getSize() != null) {
				item.setSize(response.getSize());
			}
			item.setDisplayGrade(response.getDisplayGrade());
			item.setSlabDisplay(response.getSlabDisplay());
			item.setMeasureDisplay(response.getMeasureDisplay());
			item.setPriceDisplay(response.getPriceDisplay());
			item.setQuantityDisplay(response.getQuantityDisplay());
			item.setResponseRowHtml(response.getResponseRowHtml());
		}

		item.refreshNormalizedComments();
		return item;
	}

	public void setComment(String comment) {
		this.comment = comment;
		this.normalizedComment = UnicodeNormalizer.normalize(comment);
	}

	public void setSwapComment(String swapComment) {
		this.swapComment = swapComment;
		this.normalizedSwapComment = UnicodeNormalizer.normalize(swapComment);
	}

	private void refreshNormalizedComments() {
		if (comment != null && normalizedComment == null) {
			normalizedComment = UnicodeNormalizer.normalize(comment);
		}
		if (swapComment != null && normalizedSwapComment == null) {
			normalizedSwapComment = UnicodeNormalizer.normalize(swapComment);
		}
	}
}
