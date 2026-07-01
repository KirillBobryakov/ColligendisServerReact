package com.colligendis.server.parser.meshok.model;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Filter {
	public static final String TYPE_AUCTION = "auction";
	public static final String TYPE_FIXEDPRICE = "fixedPrice";
	public static final String TYPE_ALL = null;

	Boolean availability = null;
	Integer categoryId;
	List<Integer> excludedCategoryIds = null;
	String searchString = null;
	String status = "active";
	ArrayList<String> showOnly = new ArrayList<>();
	String timeline = null;
	Location location = null;
	String condition = null;
	String type = null;
	String priceStart = null;
	String priceEnd = null;
	String quantity = null;
	String properties = null;
	String tags = null;
	String excludedSellers = null;
	Integer sellerId = null;
	Integer bidderId = null;
	String related = null;
	String soldStatus = null;
	String fromT = null;
	String tillT = null;
	String endsFromT = null;
	String endsTillT = null;
	String fromD = null;
	String tillD = null;
	String endsFromD = null;
	String endsTillD = null;
	String standardDescriptionId = null;
	int page = 1;
	int pageSize = 200;
	Sort sort = null;
}
