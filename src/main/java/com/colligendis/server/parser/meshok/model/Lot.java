package com.colligendis.server.parser.meshok.model;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Lot {

	List<AdditionalProperty> additionalProperties;
	String beginDate;
	int bidsCount;
	int categoryId;
	int altCategoryId;
	City city;
	int condition;
	int availability;
	String currency;
	Delivery delivery;
	String endDate;
	int watchCount;
	int hitsCount;

	Long id;
	Long newId;

	List<Picture> pictures;
	boolean isAntisniperEnabled;
	boolean isEndDateExtended;
	boolean blocked;
	boolean banned;
	boolean delisted;
	boolean isBargainAvailable;
	boolean isFeatured;
	int minRating;
	List<String> paymentMethods;
	int picsCount;
	int picsVersion;
	float price;
	float normalizedPrice;
	int quantity;
	int soldQuantity;
	int status;
	Seller seller;

	float startPrice;
	float strikePrice;

	List<String> tags;

	String title;

	String type;
	boolean markedAsBold;
	boolean isTemporarilyBlocked;
	float charityPercent;
	boolean hasReposts;
	Boolean isPremium;
	boolean isSafeDealEnabled;
	boolean isDiscountDisabled;
	Integer ageCategory;

	/**
	 * My properties
	 */
	Integer maxBid;
	boolean closed = false;

	Buyer buyer;

	List<LotBid> lotBids = new ArrayList<>();

}
