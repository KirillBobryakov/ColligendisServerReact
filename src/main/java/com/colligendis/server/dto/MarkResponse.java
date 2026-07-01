package com.colligendis.server.dto;

import java.util.Map;

import com.colligendis.server.util.LocalImageUrls;

public record MarkResponse(
		String nid,
		String code,
		String name,
		String description,
		String imageUrl) {

	public static MarkResponse fromMap(Map<String, Object> map) {
		if (map == null) {
			return new MarkResponse("", "", "", "", "");
		}
		final String pictureLocalPath = stringFromMap(map, "pictureLocalPath");
		return new MarkResponse(
				stringFromMap(map, "nid"),
				stringFromMap(map, "code"),
				stringFromMap(map, "name"),
				stringFromMap(map, "description"),
				LocalImageUrls.toClientUrl(pictureLocalPath));
	}

	private static String stringFromMap(Map<String, Object> map, String key) {
		Object value = map.get(key);
		return value == null ? "" : value.toString().trim();
	}
}
