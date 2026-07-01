package com.colligendis.server.database.numista.model;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.util.UnicodeNormalizer;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Dynasty, house, extended period, or any other group of ruling authorities
 *
 * Information takes from <a href=
 * "https://en.numista.com/help/add-or-modify-a-ruling-authority-in-the-catalogue-192.html">Numista</a>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class RulingAuthorityGroup extends AbstractNode {

	public static final String LABEL = "RULING_AUTHORITY_GROUP";

	private String nid;
	private String name;

	private String normalizedName;

	public RulingAuthorityGroup(String nid, String name) {
		this.nid = nid;
		this.name = name;
		this.normalizedName = UnicodeNormalizer.normalize(name);
	}

	public void setName(String name) {
		this.name = name;
		this.normalizedName = UnicodeNormalizer.normalize(name);
	}

}
