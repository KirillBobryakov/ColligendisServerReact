package com.colligendis.server.database.numista.model;

import java.util.ArrayList;
import java.util.List;

import com.colligendis.server.database.AbstractNode;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class Variant extends AbstractNode {

	public static final String LABEL = "VARIANT";

	private String nid;

	private Boolean dated;

	// Gregorian from year
	private Integer fromGregorianYear;

	// Gregorian till year
	private Integer tillGregorianYear;

	private Integer dateGregorianYear;
	private Integer dateMonth;
	private Integer dateDay;

	public static final String WITH_SIGNATURE = "WITH_SIGNATURE";
	private List<Signature> signatures = new ArrayList<>();

	private Integer mintage;

	public static final String WITH_SPECIFIED_MINT = "WITH_SPECIFIED_MINT";
	private SpecifiedMint specifiedMint;

	public static final String WITH_MARK = "WITH_MARK";
	private List<Mark> marks = new ArrayList<>();

	public static final String HAS_CATALOGUE_REFERENCES = "HAS_CATALOGUE_REFERENCES";
	private List<CatalogueReference> catalogueReferences = new ArrayList<>();

	private String comment;

	// Use this field for mark the variant as a stale
	private Boolean deletedOnNumista;

	public Variant(String nid) {
		this.nid = nid;
	}

}
