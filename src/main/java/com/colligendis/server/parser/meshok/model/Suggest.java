package com.colligendis.server.parser.meshok.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Suggest {

	String text;
	Object[] words;
	Object[] categories;
	Object[] queries;

}
