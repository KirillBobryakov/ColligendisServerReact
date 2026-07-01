package com.colligendis.server.dto;

import java.util.List;

public record CollectibleTypeParseSummaryResponse(
		int totalTypes,
		int createdCount,
		int updatedCount,
		List<CollectibleTypeSummaryItem> types) {

	public record CollectibleTypeSummaryItem(
			String code,
			String name,
			int countNTypesOnNumista,
			String parentCode) {
	}
}
