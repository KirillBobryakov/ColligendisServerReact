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
public class Denomination extends AbstractNode {
	public static final String LABEL = "DENOMINATION";

	private String nid;

	private String fullName;
	private String normalizedFullName;

	private String name;
	private String normalizedName;

	private Float numericValue;

	public static final String UNDER_CURRENCY = "UNDER_CURRENCY";
	private Currency currency;

	public Denomination(String nid, String name, String fullName, Float numericValue) {
		this.nid = nid;
		this.name = name;
		this.normalizedName = UnicodeNormalizer.normalize(name);
		this.fullName = fullName;
		this.normalizedFullName = UnicodeNormalizer.normalize(fullName);
		this.numericValue = numericValue;
	}

	public void setName(String name) {
		this.name = name;
		this.normalizedName = UnicodeNormalizer.normalize(name);
	}

	public void setFullName(String fullName) {
		this.fullName = fullName;
		this.normalizedFullName = UnicodeNormalizer.normalize(fullName);
	}
}
