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

	/**
	 * Value of the Numista contribution mint-row text input ({@code mint_identifierN}),
	 * e.g. {@code <input type="text" name="mint_identifier0" value="..." ...>}.
	 * Distinguishes multiple mint rows on the same NType; may be empty.
	 */
	private String identifier;

	public static final String WITH_MINT = "WITH_MINT";
	private Mint mint;

	public static final String WITH_MINTMARK = "WITH_MINTMARK";
	private Mintmark mintmark;
}
