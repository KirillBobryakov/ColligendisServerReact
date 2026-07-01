package com.colligendis.server.parser.numista.collection;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import lombok.Builder;
import lombok.Data;

/**
 * Form body for {@code POST https://en.numista.com/vous/remove_collection_item.php}.
 */
@Data
@Builder
public class NumistaCollectionRemoveRequest {

	public static final String REMOVE_URL = "https://en.numista.com/vous/remove_collection_item.php";

	private String item;
	private String version;
	private String collectible;

	public MultiValueMap<String, String> toFormData() {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		add(form, "item", item);
		add(form, "version", version);
		add(form, "collectible", collectible);
		return form;
	}

	private static void add(MultiValueMap<String, String> form, String key, String value) {
		if (StringUtils.hasText(value)) {
			form.add(key, value);
		}
	}
}
