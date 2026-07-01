package com.colligendis.server.database.numista.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NumistaCollectionItemPropertiesTest {

	@Test
	void propertiesMapExcludesLinkedNodes() {
		NumistaCollectionItem item = new NumistaCollectionItem();
		item.setCoinId("45564");
		item.setVersionId("193184");
		item.setNumistaCollectionItemId("58284093");
		item.setQuantity(1);
		item.setGrade("spl");

		StorageLocation storage = new StorageLocation();
		storage.setUuid("storage-uuid");
		storage.setName("Ushkov");
		item.setStorageLocation(storage);

		AcquisitionPlace place = new AcquisitionPlace();
		place.setUuid("place-uuid");
		place.setName("Meshok");
		item.setAcquisitionPlace(place);

		var properties = item.getPropertiesMap();
		var query = item.getPropertiesQuery();

		assertThat(properties)
				.containsEntry("coinId", "45564")
				.containsEntry("versionId", "193184")
				.containsEntry("numistaCollectionItemId", "58284093")
				.doesNotContainKey("storageLocation")
				.doesNotContainKey("acquisitionPlace")
				.doesNotContainKey("nType")
				.doesNotContainKey("variant");
		assertThat(query)
				.contains("coinId: $coinId")
				.doesNotContain("storageLocation")
				.doesNotContain("acquisitionPlace");
	}
}
