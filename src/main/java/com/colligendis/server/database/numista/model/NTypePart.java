package com.colligendis.server.database.numista.model;

import java.util.ArrayList;
import java.util.List;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.database.numista.model.techdata.LetteringScript;
import com.colligendis.server.database.numista.model.techdata.PART_TYPE;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class NTypePart extends AbstractNode {

	public static final String LABEL = "NTYPE_PART";

	public NTypePart(PART_TYPE partType) {
		this.partType = partType;
	}

	private PART_TYPE partType;

	public static final String ENGRAVING_WAS_DONE_BY = "ENGRAVING_WAS_DONE_BY";
	private List<Artist> engravers = new ArrayList<>();

	public static final String DESIGN_WAS_DONE_BY = "DESIGN_WAS_DONE_BY";
	private List<Artist> designers = new ArrayList<>();

	private String description;
	private String lettering;

	public static final String WRITE_ON_SCRIPT = "WRITE_ON_SCRIPT";
	private List<LetteringScript> letteringScripts = new ArrayList<>();

	private String unabridgedLegend;
	private String letteringTranslation;
	private String letteringTranslationRu;
	private String picture;

}
