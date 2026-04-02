package com.colligendis.server.database.numista.model;

import java.util.ArrayList;
import java.util.List;

import com.colligendis.server.database.AbstractNode;
import com.colligendis.server.database.numista.model.techdata.Composition;
import com.colligendis.server.database.numista.model.techdata.Shape;
import com.colligendis.server.database.numista.model.techdata.Technique;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Mono;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class NType extends AbstractNode {
	public final static String LABEL = "NTYPE";

	public NType(String nid) {
		this.nid = nid;
	}

	public String nid;

	public String title;

	public static final String HAS_COLLECTIBLE_TYPE = "HAS_COLLECTIBLE_TYPE";
	private CollectibleType collectibleType;

	public static final String ISSUED_BY = "ISSUED_BY";
	private Issuer issuer;

	public static final String DURING_OF_RULER = "DURING_OF_RULER";
	private List<RulingAuthority> rulingAuthorities = new ArrayList<>();

	public static final String ISSUED_BY_ISSUING_ENTITY = "ISSUED_BY_ISSUING_ENTITY";
	private List<IssuingEntity> issuingEntities = new ArrayList<>();

	public final static String HAS_CURRENCY = "HAS_CURRENCY";
	private String currencyUuid;
	private Mono<Currency> currency;

	public final static String DENOMINATED_IN = "DENOMINATED_IN";
	private Denomination denomination;

	public static final String COMMEMORATE_FOR = "COMMEMORATE_FOR";
	private CommemoratedEvent commemoratedEvent;

	public static final String WITH_SERIES = "WITH_SERIES";
	private Series series;

	/*
	 * Enter the date when the banknote was issued. The date of issue can be the
	 * date when a banknote started to enter circulation or the date when a
	 * commemorative banknote started to be available for sale. Use the official
	 * date when it exists. If different varieties of the banknote were issued at
	 * different date, enter the first date; you can specify the other dates in the
	 * comments.
	 * 
	 * The date should be entered in yyyy-mm-dd format. Should the precise day or
	 * the precise day and month not be known, “00” can be used:
	 * 2001-12-31
	 * 1875-00-00
	 * 
	 * If the banknote was never issued, for example in case of change of currency
	 * before the banknote was released, check “Never issued”.
	 * 
	 * For items that are not intended to be issued, the field should be blank.
	 */

	private String yearIssueDate;
	private String monthIssueDate;
	private String dayIssueDate;

	/*
	 * Select the appropriate option:
	 * Unknown: for coins that were never in circulation, such as patterns, and for
	 * coins with an uncertain legal tender status.
	 * No: for coins that are currently accepted as legal tender
	 * Yes: for coins that are no longer legal tender.
	 * 
	 * Date: for demonetized coins, record the date of the withdrawal of the legal
	 * tender status as yyyy-mm-dd. Note that this date may be different from the
	 * date of the retirement from circulation. Should the precise day not be known,
	 * “00” can be used:
	 * 2001-12-31
	 * 1875-00-00
	 */
	/*
	 * Has only 3 values:
	 * 0 - No, Didn't demonetized
	 * 1 - Yes, demonetized
	 * 2 - Unknown
	 */
	private String demonetized;
	private String demonetizationYear;
	private String demonetizationMonth;
	private String demonetizationDay;

	public static final String HAS_CATALOGUE_REFERENCES = "HAS_CATALOGUE_REFERENCES";
	private List<CatalogueReference> catalogueReferences = new ArrayList<>();

	public static final String HAS_COMPOSITION = "HAS_COMPOSITION";
	private Composition composition;

	public static final String HAS_SHAPE = "HAS_SHAPE";
	private Shape shape;

	private String shapeAdditionalDetails;

	private Double weight;

	private Double size;
	private Double size2;
	private Double thickness;

	public static final String HAS_TECHNIQUES = "HAS_TECHNIQUES";
	private List<Technique> techniques = new ArrayList<>();

	private String techniqueAdditionalDetails;

	private String alignment;

}
