package com.colligendis.server.parser.meshok.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Picture {

	String url;
	Thumbnail thumbnail;
	float ratio;

}
