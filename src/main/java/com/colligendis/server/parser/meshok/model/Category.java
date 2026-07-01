package com.colligendis.server.parser.meshok.model;

import java.util.HashMap;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Category {

	public static final int COINS = 252;
	public static final int BONES = 786;
	public static final int TOKENS = 1796;

	public static final HashMap<Integer, Category> categories = new HashMap<>() {
		{
			put(252, new Category(252, "Монеты", 140));
			put(786, new Category(786, "Банкноты и Боны", 140));
			put(1796, new Category(1796, "Токены", 140));
		}
	};

	private Category(int id, String name, int parentId) {
		this.id = id;
		this.name = name;
		this.parentId = parentId;
	}

	List<Integer> childs;
	String extraName;
	int id;
	int level;
	int lotsCount;
	String name;
	int parentId;

}
