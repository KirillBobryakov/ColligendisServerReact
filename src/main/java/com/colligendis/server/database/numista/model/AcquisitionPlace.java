package com.colligendis.server.database.numista.model;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.util.UnicodeNormalizer;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class AcquisitionPlace extends AbstractNode {

	public static final String LABEL = "ACQUISITION_PLACE";

	private String name;
	private String normalizedName;

	public AcquisitionPlace(String name) {
		setName(name);
	}

	public void setName(String name) {
		this.name = name;
		this.normalizedName = UnicodeNormalizer.normalize(name);
	}
}
