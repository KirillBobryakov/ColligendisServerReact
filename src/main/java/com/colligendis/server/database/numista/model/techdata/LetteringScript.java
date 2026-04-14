package com.colligendis.server.database.numista.model.techdata;

import com.colligendis.server.database.AbstractNode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class LetteringScript extends AbstractNode {
	public static final String LABEL = "LETTERING_SCRIPT";

	private String nid;
	private String name;

}
