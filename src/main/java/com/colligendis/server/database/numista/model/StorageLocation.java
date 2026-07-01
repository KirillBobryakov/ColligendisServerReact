package com.colligendis.server.database.numista.model;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.util.UnicodeNormalizer;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class StorageLocation extends AbstractNode {

	public static final String LABEL = "STORAGE_LOCATION";

	private String name;
	private String normalizedName;

	public StorageLocation(String name) {
		setName(name);
	}

	public void setName(String name) {
		this.name = name;
		this.normalizedName = UnicodeNormalizer.normalize(name);
	}
}
