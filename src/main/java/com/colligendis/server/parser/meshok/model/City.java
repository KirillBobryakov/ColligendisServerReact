package com.colligendis.server.parser.meshok.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class City {

	String country;
	int countryId;
	int id;
	String name;
	int popularity;
	String region;
	int regionId;
	Boolean isNameUnique;
}
