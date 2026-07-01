package com.colligendis.server.dto;

import java.util.ArrayList;
import java.util.List;

import com.colligendis.server.database.numista.model.NumistaCollectionItem;

import lombok.Data;

@Data
public class NumistaCollectionItemResponse {
	private String uuid;
	private String numistaCollectionItemId;
	private String coinId;
	private String versionId;
	private Integer quantity;
	private String grade;
	private String displayGrade;
	private String value;
	private String comment;
	private Boolean forSwap;
	private String swapComment;
	private String acquisitionDate;
	private String serialNumber;
	private String internalId;
	private String size;
	private String storageLocationUuid;
	private String storageLocationName;
	private String acquisitionPlaceUuid;
	private String acquisitionPlaceName;
	private String priceDisplay;
	private String quantityDisplay;

	public static NumistaCollectionItemResponse from(NumistaCollectionItem item) {
		NumistaCollectionItemResponse dto = new NumistaCollectionItemResponse();
		dto.setUuid(item.getUuid());
		dto.setNumistaCollectionItemId(item.getNumistaCollectionItemId());
		dto.setCoinId(item.getCoinId());
		dto.setVersionId(item.getVersionId());
		dto.setQuantity(item.getQuantity());
		dto.setGrade(item.getGrade());
		dto.setDisplayGrade(item.getDisplayGrade());
		dto.setValue(item.getValue());
		dto.setComment(item.getComment());
		dto.setForSwap(item.getForSwap());
		dto.setSwapComment(item.getSwapComment());
		dto.setAcquisitionDate(item.getAcquisitionDate());
		dto.setSerialNumber(item.getSerialNumber());
		dto.setInternalId(item.getInternalId());
		dto.setSize(item.getSize());
		dto.setPriceDisplay(item.getPriceDisplay());
		dto.setQuantityDisplay(item.getQuantityDisplay());
		if (item.getStorageLocation() != null) {
			dto.setStorageLocationUuid(item.getStorageLocation().getUuid());
			dto.setStorageLocationName(item.getStorageLocation().getName());
		}
		if (item.getAcquisitionPlace() != null) {
			dto.setAcquisitionPlaceUuid(item.getAcquisitionPlace().getUuid());
			dto.setAcquisitionPlaceName(item.getAcquisitionPlace().getName());
		}
		return dto;
	}
}
