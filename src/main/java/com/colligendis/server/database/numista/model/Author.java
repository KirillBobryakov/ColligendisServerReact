package com.colligendis.server.database.numista.model;

import java.util.ArrayList;
import java.util.List;

import com.colligendis.server.database.AbstractNode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class Author extends AbstractNode {
	public static final String LABEL = "AUTHOR";

	private String code;
	private String name;
	private String portraitUrl;
	private String biography;

	public Author(String code, String name) {
		this.code = code;
		this.name = name;
	}

	public static final String AUTHORED = "AUTHORED";
	private List<Catalogue> catalogues = new ArrayList<>();

	public static final String EDITED = "EDITED";
	private List<Catalogue> editedCatalogues = new ArrayList<>();

}
