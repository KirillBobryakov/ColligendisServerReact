package com.colligendis.server.parser.meshok.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Delivery {

	int abroadDelivery;
	float countryPrice;
	int localDelivery;
	float localPrice;
	boolean soloDelivery;
	float worldPrice;

}
