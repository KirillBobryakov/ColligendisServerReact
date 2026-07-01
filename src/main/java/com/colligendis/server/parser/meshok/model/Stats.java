package com.colligendis.server.parser.meshok.model;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Stats {
	List<Category> categories;
	Count count;

}
