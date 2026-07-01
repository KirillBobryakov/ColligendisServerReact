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
public class CommemoratedEvent extends AbstractNode {
	public static final String LABEL = "COMMEMORATED_EVENT";

	private String name;
	private String normalizedName;

	public CommemoratedEvent(String name) {
		this.name = name;
		this.normalizedName = UnicodeNormalizer.normalize(name);
	}

	public void setName(String name) {
		this.name = name;
		this.normalizedName = UnicodeNormalizer.normalize(name);
	}
}
