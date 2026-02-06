package com.colligendis.server.database.numista.model;

import java.time.Year;
import java.util.ArrayList;

import com.colligendis.server.database.AbstractNode;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/*
 * Definition
 *
 * A currency is a system of monetary units with fixed relative values. Currencies consist of one main unit and, optionally, fractional subunits or super units. The main currency unit is usually defined by law, by circulation prevalence, or by common accounting practice.
 *
 * When known, the relative value of the subunits and super units is described in reference to the main currency unit. The main unit is used to specify the face values in figure form of all items within that currency :
 * 1 Denarius = 2 Silver Quinarii = 4 Sestertii = 8 Dupondii = 16 Asses = 32 Semisses = 64 Quadrantes • 1 Aureus = 2 Gold Quinarii = 25 Denarii
 * Splitting currencies
 *
 * One issuer may have multiple currencies when redenominations occur. Redenominations may not necessarily be accompanied by a change of unit names. Redenominations may occur due to inflation, decimalisation, currency unions, monetary reforms, etc.
 * One issuer may also have multiple currencies when unrelated systems of currency units exist in parallel with a fluctuating exchange rate (for example, early thalers and ducats).
 * Currencies are not created for:
 * Series (by date, subject, etc)
 * Types of currency (for example, reserve notes, silver certificates, trials, patterns, etc.)
 * Minor name changes (for example, “new Turkish lira” became “Turkish lira” in 2009)
 * Changes, additions, or withdrawals of certain denominations (for example, withdrawal of the 500 euro banknote)
 * Debasement, changes in exchange rates, appreciation and depreciation, inflation and deflation, devaluation and revaluation
 * All currencies are listed in English in the database, according to the main listings (not alternative forms) of Oxford English Dictionary and Wiktionary.com.
 * How to request the addition of a new currency?
 *
 * Please post a request on the forum indicating:
 * the name of the currency in English (and if possible also in French and Spanish)
 * the issuer where this currency was used, and the corresponding date range
 * if known, the relative value of the subunits and super units described in reference to the main currency unit
 * if there are no entries yet on Numista, the link to an auction site or a reference catalogue presenting at least one coin or banknote using this currency
 * if they already exist, the link of one or more Numista records that you want to classify under this currency
 *
 * It is not required to provide all the information above. However, complete requests can be verified and added to the database quicker by the catalogue admins.
 *
 * Information takes from https://en.numista.com/help/add-or-modify-a-currency-in-the-catalogue-194.html
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class Currency extends AbstractNode {
	public static final String LABEL = "CURRENCY";

	private String nid;
	private String fullName;
	private String name;

	/**
	 * Kind of currency like as
	 * notgeld - Mark (notgeld, 1914-1924)
	 * Occupation currency - Mark (Occupation currency, 1918), Rouble (Occupation
	 * currency, 1916)
	 */
	private String kind;

	public static final String CIRCULATE_WHEN_BEEN = "CIRCULATE_WHEN_BEEN";
	private Issuer issuer;

	public static final String CIRCULATED_FROM = "CIRCULATED_FROM";
	private ArrayList<Year> circulatedFromYears = new ArrayList<>();

	public static final String CIRCULATED_TILL = "CIRCULATED_TILL";
	private ArrayList<Year> circulatedTillYears = new ArrayList<>();

	// todo: it might throw an error while parsing numista page
	// @Relationship(type = Denomination.UNDER_CURRENCY, direction =
	// Relationship.Direction.INCOMING)
	// private ArrayList<Denomination> denominations = new ArrayList<>();

	private Boolean isActual;

	public Currency(String nid) {
		this.nid = nid;
	}

	public Currency(String nid, String fullName) {
		this.nid = nid;
		this.fullName = fullName;
	}

}
