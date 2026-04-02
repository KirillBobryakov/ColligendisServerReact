package com.colligendis.server.service;

import com.colligendis.server.logger.LogExecutionTime;
import com.colligendis.server.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;
import org.neo4j.driver.reactivestreams.ReactiveSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

	private final Driver driver;
	private final PasswordEncoder passwordEncoder;

	@LogExecutionTime
	public Mono<User> createUser(User user) {
		log.info("Creating new user with username: {}", user.getUsername());

		user.setId(UUID.randomUUID().toString());
		user.setPassword(passwordEncoder.encode(user.getPassword()));
		user.setCreatedAt(LocalDateTime.now());
		user.setUpdatedAt(LocalDateTime.now());
		user.setActive(true);

		String cypher = """
				CREATE (u:User {
				    id: $id,
				    username: $username,
				    email: $email,
				    password: $password,
				    firstName: $firstName,
				    lastName: $lastName,
				    createdAt: $createdAt,
				    updatedAt: $updatedAt,
				    active: $active
				})
				RETURN u
				""";

		return Flux.usingWhen(
				Mono.just(driver.session(ReactiveSession.class, SessionConfig.builder().withDatabase("neo4j").build())),
				(ReactiveSession session) -> Flux.from(session.run(cypher, Values.parameters(
						"id", user.getId(),
						"username", user.getUsername(),
						"email", user.getEmail(),
						"password", user.getPassword(),
						"firstName", user.getFirstName(),
						"lastName", user.getLastName(),
						"createdAt", user.getCreatedAt().toString(),
						"updatedAt", user.getUpdatedAt().toString(),
						"active", user.isActive()))).flatMap(result -> Flux.from(result.records())
								.map(record -> mapToUser(record.get("u").asMap()))),
				ReactiveSession::close)
				.next()
				.doOnSuccess(savedUser -> log.info("User created successfully with id: {}", savedUser.getId()))
				.doOnError(error -> log.error("Failed to create user: {}", error.getMessage()));
	}

	@LogExecutionTime
	public Mono<User> findById(String id) {
		log.debug("Finding user by id: {}", id);

		String cypher = "MATCH (u:User {id: $id}) RETURN u";

		return Flux.usingWhen(
				Mono.just(driver.session(ReactiveSession.class, SessionConfig.builder().withDatabase("neo4j").build())),
				(ReactiveSession session) -> Flux.from(session.run(cypher, Values.parameters("id", id)))
						.flatMap(result -> Flux.from(result.records())
								.map(record -> mapToUser(record.get("u").asMap()))),
				ReactiveSession::close)
				.next()
				.doOnSuccess(user -> {
					if (user != null) {
						log.debug("User found: {}", user.getUsername());
					} else {
						log.debug("User not found with id: {}", id);
					}
				});
	}

	@LogExecutionTime
	public Mono<User> findByUsername(String username) {
		log.debug("Finding user by username: {}", username);

		String cypher = "MATCH (u:User {username: $username}) RETURN u";

		return Flux.usingWhen(
				Mono.just(driver.session(ReactiveSession.class, SessionConfig.builder().withDatabase("neo4j").build())),
				(ReactiveSession session) -> Flux.from(session.run(cypher, Values.parameters("username", username)))
						.flatMap(result -> Flux.from(result.records())
								.map(record -> mapToUser(record.get("u").asMap()))),
				ReactiveSession::close)
				.next()
				.doOnSuccess(user -> {
					if (user != null) {
						log.debug("User found with username: {}", username);
					} else {
						log.debug("User not found with username: {}", username);
					}
				});
	}

	@LogExecutionTime
	public Flux<User> findAllUsers() {
		log.info("Retrieving all users");

		String cypher = "MATCH (u:User) RETURN u";

		return Flux.usingWhen(
				Mono.just(driver.session(ReactiveSession.class, SessionConfig.builder().withDatabase("neo4j").build())),
				(ReactiveSession session) -> Flux.from(session.run(cypher))
						.flatMap(result -> Flux.from(result.records())
								.map(record -> mapToUser(record.get("u").asMap()))),
				ReactiveSession::close)
				.doOnComplete(() -> log.info("All users retrieved"))
				.doOnError(error -> log.error("Error retrieving users: {}", error.getMessage()));
	}

	@LogExecutionTime
	public Mono<User> updateUser(String id, User user) {
		log.info("Updating user with id: {}", id);

		String cypher = """
				MATCH (u:User {id: $id})
				SET u.email = $email,
				    u.firstName = $firstName,
				    u.lastName = $lastName,
				    u.updatedAt = $updatedAt
				RETURN u
				""";

		return Flux.usingWhen(
				Mono.just(driver.session(ReactiveSession.class, SessionConfig.builder().withDatabase("neo4j").build())),
				(ReactiveSession session) -> Flux.from(session.run(cypher, Values.parameters(
						"id", id,
						"email", user.getEmail(),
						"firstName", user.getFirstName(),
						"lastName", user.getLastName(),
						"updatedAt", LocalDateTime.now().toString()))).flatMap(result -> Flux.from(result.records())
								.map(record -> mapToUser(record.get("u").asMap()))),
				ReactiveSession::close)
				.next()
				.doOnSuccess(updatedUser -> log.info("User updated successfully: {}", id))
				.doOnError(error -> log.error("Failed to update user: {}", error.getMessage()));
	}

	@LogExecutionTime
	public Mono<Void> deleteUser(String id) {
		log.info("Deleting user with id: {}", id);

		String cypher = "MATCH (u:User {id: $id}) DELETE u";

		return Mono.<Void, ReactiveSession>usingWhen(
				Mono.just(driver.session(ReactiveSession.class, SessionConfig.builder().withDatabase("neo4j").build())),
				session -> Mono.from(session.run(cypher, Values.parameters("id", id)))
						.flatMap(result -> Mono.from(result.consume())).then(),
				session -> Mono.from(session.close()))
				.doOnSuccess(v -> log.info("User deleted successfully: {}", id))
				.doOnError(error -> log.error("Failed to delete user: {}", error.getMessage()));
	}

	private User mapToUser(Map<String, Object> map) {
		User user = new User();
		user.setId((String) map.get("id"));
		user.setUsername((String) map.get("username"));
		user.setEmail((String) map.get("email"));
		user.setPassword((String) map.get("password"));
		user.setFirstName((String) map.get("firstName"));
		user.setLastName((String) map.get("lastName"));
		user.setCreatedAt(LocalDateTime.parse((String) map.get("createdAt")));
		user.setUpdatedAt(LocalDateTime.parse((String) map.get("updatedAt")));
		user.setActive((Boolean) map.get("active"));
		return user;
	}
}
