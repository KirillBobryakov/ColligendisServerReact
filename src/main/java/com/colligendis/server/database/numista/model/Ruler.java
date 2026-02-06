package com.colligendis.server.database.numista.model;

import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.colligendis.server.database.AbstractNode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/*
 * Definition
 *
 * The ruling authority is an individual head of state represented on numismatic or exonumia items (for example, Charlemagne). Heads of state are understood in a broad sense, including monarchs, regents, governors, lords, ranking nobles or clergy, leaders, officers, usurpers, etc.
 *
 * When heads of state are not individually represented on numismatic or exonumia items, the ruling authority can be the form of government (for example, free imperial city), a specific government or governing body (for example, second republic), a distinct historic period (for example, German occupation, sede vacante), or the official name of the issuer, when the name changed throughout history (for example, Kingdom of Swaziland, Kingdom of Eswatini).
 *
 * Ruling authorities are included in the Numista database only if they issued coins, banknotes or exonumia items. Multiple issuers can share the same ruling authorities.
 * How to request the addition of a new ruling authority?
 *
 * Please post a request on the forum indicating:
 * the name of the ruling authority in English and in the local language (and if possible also in French and Spanish)
 * the name of issuing authorities (as they appear on Numista) in which they ruled, with titles and date ranges for each of them
 * if the ruling authority had different successive titles, we need to create one version for each date range
 * its Wikidata code (for example, Q40787 for tsar Nicholas II)
 * if applicable, the dynasty, house, or more generally the group to which the ruler belongs (for example, House of Romanov for tsar Nicholas II)
 * if there are no entries yet on Numista, the link to an auction site or a reference catalogue presenting at least one coin or banknote from this ruler
 * if they already exist, the link of one or more Numista records that you want to classify under this ruling authority
 *
 * It is not required to provide all the information above. However, complete requests can be verified and added to the database quicker by the catalogue admins.
 * Rules for ruling authority naming
 *
 * Regnal numbers follow the name in Roman numerals with no ordinal indicators.
 * Henry I
 * Henry Ier, Henry I., Henry 1st
 * Cognomens (nicknames) should not be used, except if thy are very common is included.
 * Suleiman I, Vlad III
 * Suleiman I the Magnificent the Lawgiver, Vlad the Impaler Dracula
 * Sobriquets are used only when they are universally better known than the name of the ruler:
 * Caligula (for Roman emperor Gaius Germanicus)
 * Grandmother of Europe (for British queen Victoria)
 * When ruling authority is a form of government:
 * Technical details regarding the form of government are not included
 * free imperial city || federal state || socialist republic || province of the French colony of Madagascar
 * parliamentary constitutional elective monarchy
 * The name of the issuer is not repeated in the name of the ruling authority
 * Republic
 * Republic of Italy
 * English is used for the names of all ruling authorities.
 * Charles
 * Carlos || Karl || Carol
 *
 * Information takes from https://en.numista.com/help/add-or-modify-a-ruling-authority-in-the-catalogue-192.html
 */
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
public class Ruler extends AbstractNode {
    public static final String LABEL = "RULER";

    public static final List<String> RULERS = Arrays.asList(
            "Period", "Archduke", "Ban", "Bishop", "Caesar", "Camerlengo", "Doge", "Duchess", "Duke", "Emir", "Emperor",
            "Empress",
            "Grandmaster", "Grand duchess", "Grand duke", "Grand Prince",
            "Khan", "King", "Landgrave", "Lord", "Margrave", "Margravine", "Master",
            "Pope", "President", "Prime minister", "Prince", "Prince-archbishop", "Prince elector", "Prince-bishop",
            "Queen", "Regent", "Ruling authority", "Shah", "Sultan", "Tsar", "Voivode");

    private String nid;
    private String name;
    private String rulerType;

    public static final String RULES_WHEN_BEEN = "RULES_WHEN_BEEN";
    private Issuer issuer;

    public static final String GROUP_BY = "GROUP_BY";
    private RulerGroup rulerGroup;

    public static final String RULES_FROM = "RULES_FROM";
    private ArrayList<Year> rulesFromYears = new ArrayList<>();

    public static final String RULES_TILL = "RULES_TILL";
    private ArrayList<Year> rulesTillYears = new ArrayList<>();

    // todo Ruling period and
    // catalogue/get_rulers.php?country=yemen_nord&e=1
    /*
     * <optgroup label="Mutawakkilite Kingdom">
     * <option value="5487">Yahya Muhammad Hamid ed-Din (1918-1948)</option>
     * <option value="5488">Ahmad bin Yahya (1948-1962)</option>
     * <option value="5502">Muhammad al-Badr (1962-1970)</option>
     * </optgroup>
     * <option value="5476">Yemen Arab Republic (1962-1990)</option>
     */

    private Boolean isActual;

    public Ruler(String nid, String name) {
        this.nid = nid;
        this.name = name;
    }

}
