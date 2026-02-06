package com.colligendis.server.database.numista.model;

import java.util.List;

import com.colligendis.server.database.AbstractNode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class Catalogue extends AbstractNode {
	public static final String LABEL = "CATALOGUE";

	private String nid; // numista id - different in
						// https://en.numista.com/catalogue/search_catalogues.php?q=Gr&e=1
						// {"id":1435,"text":"Gra","bibliography":"Hans-Ludwig Grabowski; 2020"}
	// and in
	// https://en.numista.com/literature/catalogues.php
	// <a href="https://en.numista.com/L105232">Gra</a> - L105232 is the nid
	// and in
	// https://en.numista.com/literature/contributions/modify.php?id=1381

	private String code; // catalogue code like 0euro
	private String number; // catalogue number like L101607

	private String title;
	private String title_en;

	public static final String WRITTEN_BY = "WRITTEN_BY";
	private List<Author> authors;

	private String bibliography;

	public Catalogue(String code, String number) {
		this.code = code;
		this.number = number;
	}

	// private String code;
	// private String authors;
	// private String title;
	// private String edition;
	// private String publisher;
	// private String publicationLocation;
	// private String publicationYear;
	// private String ISBN10;
	// private String ISBN13;
}
