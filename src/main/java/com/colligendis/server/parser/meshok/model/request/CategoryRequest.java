package com.colligendis.server.parser.meshok.model.request;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CategoryRequest {

	private static final String URL = "https://meshok.net/api/command/categories/get-items";

	List<Integer> identifiers;
	int childsLevel;
	boolean includeParents;

}
