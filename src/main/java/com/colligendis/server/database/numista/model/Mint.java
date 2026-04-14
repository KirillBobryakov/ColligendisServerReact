package com.colligendis.server.database.numista.model;

import com.colligendis.server.database.AbstractNode;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class Mint extends AbstractNode {
	public static final String LABEL = "MINT";

	private String nid;
	private String name;
	private String localName;

	private String place;
	private String officialWebsite;
	private String description;

	private String latitude;
	private String longitude;

	public static final String HAS_MINTMARK = "HAS_MINTMARK";
	private List<Mintmark> mintmarks = new ArrayList<>();
}
