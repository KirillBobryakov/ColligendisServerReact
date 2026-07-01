package com.colligendis.server.database;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ColligendisUserPropertiesTest {

	@Test
	void propertiesMapIncludesUsernameAndPasswordEvenWhenOnlySettersUsed() {
		ColligendisUser user = new ColligendisUser();
		user.setUsername("user@example.com");
		user.setPassword("$2a$10$encoded");

		var properties = user.getPropertiesMap();
		var query = user.getPropertiesQuery();

		assertThat(properties)
				.containsEntry("username", "user@example.com")
				.containsEntry("password", "$2a$10$encoded");
		assertThat(query)
				.contains("username: $username")
				.contains("password: $password");
	}
}
