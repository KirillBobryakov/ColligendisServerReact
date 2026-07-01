package com.colligendis.server.parser.meshok.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Includes {
	boolean lots = true;
	boolean stats = false;

	public Includes(boolean lots, boolean stats) {
		this.lots = lots;
		this.stats = stats;
	}

}
