package com.colligendis.server.database.numista.model;

import com.colligendis.server.database.AbstractNode;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class Mark extends AbstractNode {

	public static final String LABEL = "MARK";

	private String nid;
	private String code; // from file name of the picture
	private String name; // from alt attribute of the <img> tag
	private String description; // from title attribute of the <img> tag

	private String picture; // from src attribute of the <img> tag

	@JsonIgnore
	private String pictureLocalPath;
}
