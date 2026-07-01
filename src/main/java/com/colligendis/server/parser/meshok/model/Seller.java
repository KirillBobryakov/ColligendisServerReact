package com.colligendis.server.parser.meshok.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Seller {
	String avatarURL;
	String avatarThumbnailURL;
	String creationDate;
	String displayName;
	boolean isTrusted;
	int rating;
	int id;
	int subscription;
	int lotsTotalAmount;
	int lotsWithBidsAmount;
	int newLotsAmount;
	String newLotsAmountPeriod;
	int endingLotsAmount;

	boolean hasEconomyDelivery;
	boolean isBanned;
	boolean isOnHold;
}
