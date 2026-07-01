package com.colligendis.server.database.meshok;

import java.util.ArrayList;
import java.util.List;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.database.numista.model.Country;
import com.colligendis.server.database.numista.model.Issuer;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class MeshokCategory extends AbstractNode {
	public static final String LABEL = "MESHOK_CATEGORY";

	private long id;
	private int level;
	private String name;

	public static final String HAS_CHILDREN = "HAS_CHILDREN";
	private List<MeshokCategory> children = new ArrayList<>();

	public static final String HAS_PARENT = "HAS_PARENT";
	private MeshokCategory parent;

	public static final String MATCH_TO_COUNTRY = "MATCH_TO_COUNTRY";
	private List<Country> countries = new ArrayList<>();

	public static final String MATCH_TO_ISSUER = "MATCH_TO_ISSUER";
	private List<Issuer> issuers = new ArrayList<>();
}
