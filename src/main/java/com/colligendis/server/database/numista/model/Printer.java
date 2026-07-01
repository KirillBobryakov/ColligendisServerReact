package com.colligendis.server.database.numista.model;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.util.UnicodeNormalizer;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class Printer extends AbstractNode {
	public static final String LABEL = "PRINTER";

	private String nid;
	private String name;
	private String normalizedName;

	public Printer(String nid, String name) {
		this.nid = nid;
		this.name = name;
		this.normalizedName = UnicodeNormalizer.normalize(name);
	}

	public void setName(String name) {
		this.name = name;
		this.normalizedName = UnicodeNormalizer.normalize(name);
	}
}
