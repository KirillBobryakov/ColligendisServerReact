package com.colligendis.server.database.numista.model;

import java.util.ArrayList;
import java.util.List;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.database.common.model.Calendar;
import com.colligendis.server.database.common.model.Year;
import com.colligendis.server.util.UnicodeNormalizer;

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

	public static final String WITH_CALENDAR = "WITH_CALENDAR";
	private Calendar calendar;

	public static final String DATED_AT = "DATED_AT";
	private Year datedAtYear;

	public static final String DATED_FROM = "DATED_FROM";
	private Year datedFromYear;

	public static final String DATED_TILL = "DATED_TILL";
	private Year datedTillYear;

	private Integer dateMonth;
	private Integer dateDay;

	public static final String WITH_SIGNATURE = "WITH_SIGNATURE";
	private List<Signature> signatures = new ArrayList<>();

	private Integer mintage;

	private String mintLetter;

	public static final String WITH_MARK = "WITH_MARK";
	private List<Mark> marks = new ArrayList<>();

	public static final String HAS_CATALOGUE_REFERENCES = "HAS_CATALOGUE_REFERENCES";
	private List<CatalogueReference> catalogueReferences = new ArrayList<>();

	private String comment;
	private String normalizedComment;

	// Use this field for mark the variant as a stale
	private Boolean deletedOnNumista;

	public Variant(String nid) {
		this.nid = nid;
	}

	public void setComment(String comment) {
		this.comment = comment;
		this.normalizedComment = UnicodeNormalizer.normalize(comment);
	}

}
