package com.colligendis.server.database.numista.model;

import com.colligendis.server.database.AbstractNode;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class CatalogueReference extends AbstractNode {
	public static final String LABEL = "CATALOGUE_REFERENCE";

	public static final String REFERENCE_FROM = "REFERENCE_FROM";
	private Catalogue catalogue;

	private String number;

	public CatalogueReference(String number) {
		this.number = number;
	}
}
