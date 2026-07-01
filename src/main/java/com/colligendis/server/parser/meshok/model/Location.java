package com.colligendis.server.parser.meshok.model;

import java.util.HashMap;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Location {
	public static final HashMap<Integer, Location> locations = new HashMap<>() {
		{
			put(32, new Location(32, "all")); // Москва
		}
	};

	private Location(int cityId, String option) {
		this.cityId = cityId;
		this.option = option;
	}

	int cityId;
	String option;
	Boolean freeDelivery = false;
	Boolean economyDelivery = false;
	Boolean pickup = false;

}
