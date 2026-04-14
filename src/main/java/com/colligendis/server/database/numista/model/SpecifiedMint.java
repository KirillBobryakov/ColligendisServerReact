package com.colligendis.server.database.numista.model;

import com.colligendis.server.database.AbstractNode;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class SpecifiedMint extends AbstractNode {
	public static final String LABEL = "SPECIFIED_MINT";

	private String identifier;

	public static final String WITH_MINT = "WITH_MINT";
	private Mint mint;

	public static final String WITH_MINTMARK = "WITH_MINTMARK";
	private Mintmark mintmark;
}
