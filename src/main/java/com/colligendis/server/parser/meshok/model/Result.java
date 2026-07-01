package com.colligendis.server.parser.meshok.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Result {
	Lot[] lots;
	Stats stats;
	Suggest suggest;
	long[] lastSortValues;

}
