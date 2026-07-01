package com.colligendis.server.parser.meshok.model;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AdditionalProperty {
	int categoryId;
	String name;
	int propertyId;
	List<Value> values;
}
