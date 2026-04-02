package com.colligendis.server.database.numista.model;

import com.colligendis.server.database.AbstractNode;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/*
 * Some coins are issued by an entity (company, chamber of commerce, etc.) different from the government of the state and from the mint.
 * For example tokens, notgeld, and some modern commemorative coins.
 *
 * Definition
 *
 * Some items, especially banknotes, tokens and notgeld, are issued by an entity (bank, company, chamber of commerce, moneyer, etc.) different from the government of the state and from the mint.
 *
 * Coins are usually issued either by the state or by the state mint, so there is no separate entity. If a coin mentions the name of a bank, the bank is considered as the issuing entity.
 *
 * How to request the addition of a new issuing entity?
 *
 * Please post a request on the forum indicating:
 * the name of the entity in English (and if possible also in French and Spanish)
 * the name of the entity in the language of its country
 * a description of the entity
 * the issuer(s) in which this entity issued items
 * the origin country of the entity
 * the type of entity: bank, central bank, public institution, private company or moneyer
 * its Wikidata code (for example, Q806950 for Central Bank of France)
 * if there are no entries yet on Numista, the link to an auction site or a reference catalogue presenting at least one coin or banknote from this entity
 * if they already exist, the link of one or more Numista records that you want to classify under this entity
 *
 * It is not required to provide all the information above. However, complete requests can be verified and added to the database quicker by the catalogue admins.
 *
 * Name of the issuing entity
 * Translate to English the names of military units, institutions, organisations, associations, etc. The type of enterprise or its abbreviation should be omitted from titles (S.A., Ltd., GmbH., Co., AG…).
 *
 * YES= Chambre of Commerce of Narbonne
 * NOT= Chambre de Commerce de Narbonne
 *
 * YES= Bavarian Lignite Industry
 * NOT= Bayerische Braunkohlen-Industrie  ||  Bavarian Lignite Industry AG
 *
 * Information takes from https://en.numista.com/help/add-or-modify-an-issuing-entity-in-the-catalogue-193.html
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class IssuingEntity extends AbstractNode {

	public static final String LABEL = "ISSUING_ENTITY";

	private String nid;
	private String name;

	public static final String ISSUES_WHEN_BEEN = "ISSUES_WHEN_BEEN";
	private Issuer issuer;

	private Boolean isActual;

	public IssuingEntity(String nid, String name) {
		this.nid = nid;
		this.name = name;
	}

	// /catalogue/get_issuing_entities.php?country=freiburg-im-breisgau_notgeld&prefill=
	// <option value="2271">City of Rastatt</option>
	// <option value="4561">Stroebeck</option>
	// <option value="4866">Vorschuss Verein Soldau</option>

}
