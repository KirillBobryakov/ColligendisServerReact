package com.colligendis.server.parser.meshok.model.request;

import com.colligendis.server.parser.meshok.model.Filter;
import com.colligendis.server.parser.meshok.model.Includes;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GetItemsRequest {

	private static final String URL = "https://meshok.net/api/command/lots/get-items";

	boolean sellerMode = false;
	Filter filter = new Filter();
	Includes includes = new Includes(true, false);

	boolean saveSearchRequest = false;
	boolean featuredLotsFirst = true;
	boolean onlyWithPicture = true;

}
