package com.colligendis.server.database.numista.model;

import com.colligendis.server.database.AbstractNode;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class Signature extends AbstractNode {
	public static final String LABEL = "SIGNATURE";

	// https://en.numista.com/catalogue/search_signatures.php?ie=71&sie=&_type=query&term=&q=

	private String nid; // id
	private String name; // text
	private String pictureUrl; // image

}
