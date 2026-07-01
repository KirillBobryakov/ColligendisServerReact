package com.colligendis.server.parser.meshok.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LotBid {
	Integer autoBid;
	Float bid;

	Buyer buyer;

	String currencyCode;

	String date;

	int id;

	int lotId;
}
