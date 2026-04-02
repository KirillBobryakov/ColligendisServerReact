package com.colligendis.server.database.numista.model;

import java.util.ArrayList;
import java.util.List;

import com.colligendis.server.database.AbstractNode;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/*
 * Definition
 *
 * An issuer is any:
 * organised community (for example, Australia, Commune of Nice, Abbey of Saint Gall, Rauraci tribe),
 * association of such communities (for example, Eurozone, West African States, joint notgeld issuers),
 * with a claimed right to issue currency.
 *
 * An issuer may have different currencies, governments and names throughout its history.
 *
 * An autonomous part of a bigger issuer that issued coins for local usage are also considered as issuers (for example, various Roman provinces).
 *
 * Only when the territory of an issuer suffers a sudden, significant, and long-term change, resulting in a discontinuity of its currency, then the change result in a different issuer. For example the Soviet Union and modern-day Russia are listed as different issuers.
 * How to request the addition of a new issuer?
 *
 * Please post a request on the forum, indicating:
 * the name of the issuer in English (and if possible also in French and Spanish)
 * its Wikidata code (for example, Q854 for Sri Lanka)
 * if there are no entries yet on Numista, the link to an auction site or a reference catalogue presenting at least one coin or banknote from this issuer
 * if they already exist in the catalogue, the link of one or more Numista records that you want to classify under this issuer
 * if necessary, the list of other issuers to be linked by “see also” links (for example the Holy Roman Empire is linked to the Carolingian Empire)
 * if possible, a brief introduction presenting the history of this issuer with any numismatic considerations useful to the reader
 *
 * It is not required to provide all the information above. However, complete requests can be verified and added to the database quicker by the catalogue admins. Also remember to request the creation of the currencies and ruling authorities necessary for this new issuer!
 *
 * Information takes from https://en.numista.com/help/add-or-modify-an-issuing-authority-in-the-catalogue-191.html
 */

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class Issuer extends AbstractNode {

	public static final String LABEL = "ISSUER";

	public static final String CONTAINS_ISSUING_ENTITY = "CONTAINS_ISSUING_ENTITY";
	public static final String CONTAINS_CURRENCY = "CONTAINS_CURRENCY";

	/**
	 * Unique String field from Numista
	 */
	@ToString.Include
	private String numistaCode;
	/**
	 * Unique String field from Numista
	 */
	@ToString.Include
	private String name;

	private List<String> ruAlternativeNames = new ArrayList<>();

	public static final String RELATE_TO_SUBJECT = "RELATE_TO_SUBJECT";
	private Subject parentSubject;

	public static final String RELATE_TO_COUNTRY = "RELATE_TO_COUNTRY";
	private Country country;

}
