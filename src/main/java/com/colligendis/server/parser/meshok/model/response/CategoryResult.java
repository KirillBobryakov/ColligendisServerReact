package com.colligendis.server.parser.meshok.model.response;

import java.util.ArrayList;

import com.colligendis.server.parser.meshok.model.Category;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CategoryResult {
	String correlationId;
	ArrayList<Category> result;

}
