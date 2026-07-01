package com.colligendis.server.database;

import java.util.List;
import java.util.Map;

import org.springframework.util.StringUtils;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ColligendisUser extends AbstractUser {
	public final static String LABEL = "COLLIGENDIS_USER";

	public static final String HAS_ACQUISITION_PLACE = "HAS_ACQUISITION_PLACE";
	public static final String HAS_STORAGE_LOCATION = "HAS_STORAGE_LOCATION";
	public static final String HAS_IN_COLLECTION = "HAS_IN_COLLECTION";

	/**
	 * Numista session cookie ({@code Cookie} header) for authenticated API calls.
	 */
	private String numistaCookie;

	/**
	 * Meshok session cookie ({@code Cookie} header) for authenticated API calls.
	 */
	private String meshokCookie;

	/**
	 * Application roles, e.g. {@code ["ADMIN"]}. Stored as a Neo4j string array.
	 * Set manually in the database for privileged accounts.
	 */
	private List<String> roles;

	@Override
	public Map<String, Object> getPropertiesMap() {
		final Map<String, Object> properties = super.getPropertiesMap();
		putTextProperty(properties, "username", username);
		putTextProperty(properties, "password", password);
		putTextProperty(properties, "numistaCookie", numistaCookie);
		return properties;
	}

	@Override
	public String getPropertiesQuery() {
		final StringBuilder query = new StringBuilder(super.getPropertiesQuery());
		appendTextPropertyQuery(query, "username", username);
		appendTextPropertyQuery(query, "password", password);
		appendTextPropertyQuery(query, "numistaCookie", numistaCookie);
		return query.toString();
	}

	private static void putTextProperty(Map<String, Object> properties, String key, String value) {
		if (StringUtils.hasText(value)) {
			properties.put(key, value);
		}
	}

	private static void appendTextPropertyQuery(StringBuilder query, String key, String value) {
		if (StringUtils.hasText(value)) {
			query.append(", ").append(key).append(": $").append(key);
		}
	}
}
