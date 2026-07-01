package com.colligendis.server.parser.meshok.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Buyer {
	String avatarThumbnailURL;
	String avatarURL;
	int id;
	String name;
	int rating;

}
