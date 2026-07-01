package com.colligendis.server.parser.meshok.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Sort {
	public static final int ASCENDING = 0;
	public static final int DESCENDING = 1;
	public static final String END_EARLY = "endDate";

	String field;
	int direction;

}
