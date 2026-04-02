package com.colligendis.server.service;

import com.colligendis.server.logger.LogExecutionTime;
import com.colligendis.server.model.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;
import org.neo4j.driver.reactivestreams.ReactiveSession;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

	private final Driver driver;

	@LogExecutionTime
	public Mono<Product> createProduct(Product product) {
		log.info("Creating new product: {}", product.getName());

		product.setId(UUID.randomUUID().toString());
		product.setCreatedAt(LocalDateTime.now());
		product.setUpdatedAt(LocalDateTime.now());
		product.setAvailable(true);

		String cypher = """
				CREATE (p:Product {
				    id: $id,
				    name: $name,
				    description: $description,
				    price: $price,
				    quantity: $quantity,
				    category: $category,
				    createdAt: $createdAt,
				    updatedAt: $updatedAt,
				    available: $available
				})
				RETURN p
				""";

		return Flux.usingWhen(
				Mono.just(driver.session(ReactiveSession.class, SessionConfig.builder().withDatabase("neo4j").build())),
				(ReactiveSession session) -> Flux.from(session.run(cypher, Values.parameters(
						"id", product.getId(),
						"name", product.getName(),
						"description", product.getDescription(),
						"price", product.getPrice().doubleValue(),
						"quantity", product.getQuantity(),
						"category", product.getCategory(),
						"createdAt", product.getCreatedAt().toString(),
						"updatedAt", product.getUpdatedAt().toString(),
						"available", product.isAvailable()))).flatMap(result -> Flux.from(result.records())
								.map(record -> mapToProduct(record.get("p").asMap()))),
				ReactiveSession::close)
				.next()
				.doOnSuccess(savedProduct -> log.info("Product created successfully with id: {}", savedProduct.getId()))
				.doOnError(error -> log.error("Failed to create product: {}", error.getMessage()));
	}

	@LogExecutionTime
	public Mono<Product> findById(String id) {
		log.debug("Finding product by id: {}", id);

		String cypher = "MATCH (p:Product {id: $id}) RETURN p";

		return Flux.usingWhen(
				Mono.just(driver.session(ReactiveSession.class, SessionConfig.builder().withDatabase("neo4j").build())),
				(ReactiveSession session) -> Flux.from(session.run(cypher, Values.parameters("id", id)))
						.flatMap(result -> Flux.from(result.records())
								.map(record -> mapToProduct(record.get("p").asMap()))),
				ReactiveSession::close)
				.next()
				.doOnSuccess(product -> {
					if (product != null) {
						log.debug("Product found: {}", product.getName());
					} else {
						log.debug("Product not found with id: {}", id);
					}
				});
	}

	@LogExecutionTime
	public Flux<Product> findAllProducts() {
		log.info("Retrieving all products");

		String cypher = "MATCH (p:Product) RETURN p";

		return Flux.usingWhen(
				Mono.just(driver.session(ReactiveSession.class, SessionConfig.builder().withDatabase("neo4j").build())),
				(ReactiveSession session) -> Flux.from(session.run(cypher))
						.flatMap(result -> Flux.from(result.records())
								.map(record -> mapToProduct(record.get("p").asMap()))),
				ReactiveSession::close)
				.doOnComplete(() -> log.info("All products retrieved"))
				.doOnError(error -> log.error("Error retrieving products: {}", error.getMessage()));
	}

	@LogExecutionTime
	public Flux<Product> findByCategory(String category) {
		log.info("Finding products by category: {}", category);

		String cypher = "MATCH (p:Product {category: $category}) RETURN p";

		return Flux.usingWhen(
				Mono.just(driver.session(ReactiveSession.class, SessionConfig.builder().withDatabase("neo4j").build())),
				(ReactiveSession session) -> Flux.from(session.run(cypher, Values.parameters("category", category)))
						.flatMap(result -> Flux.from(result.records())
								.map(record -> mapToProduct(record.get("p").asMap()))),
				ReactiveSession::close)
				.doOnComplete(() -> log.info("Products retrieved for category: {}", category));
	}

	@LogExecutionTime
	public Flux<Product> searchProducts(String searchTerm) {
		log.info("Searching products with term: {}", searchTerm);

		String cypher = """
				MATCH (p:Product)
				WHERE p.name CONTAINS $searchTerm OR p.description CONTAINS $searchTerm
				RETURN p
				""";

		return Flux.usingWhen(
				Mono.just(driver.session(ReactiveSession.class, SessionConfig.builder().withDatabase("neo4j").build())),
				(ReactiveSession session) -> Flux.from(session.run(cypher, Values.parameters("searchTerm", searchTerm)))
						.flatMap(result -> Flux.from(result.records())
								.map(record -> mapToProduct(record.get("p").asMap()))),
				ReactiveSession::close)
				.doOnComplete(() -> log.info("Product search completed for term: {}", searchTerm));
	}

	@LogExecutionTime
	public Flux<Product> findByPriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
		log.info("Finding products in price range: {} - {}", minPrice, maxPrice);

		String cypher = "MATCH (p:Product) WHERE p.price >= $minPrice AND p.price <= $maxPrice RETURN p";

		return Flux.usingWhen(
				Mono.just(driver.session(ReactiveSession.class, SessionConfig.builder().withDatabase("neo4j").build())),
				(ReactiveSession session) -> Flux.from(session.run(cypher, Values.parameters(
						"minPrice", minPrice.doubleValue(),
						"maxPrice", maxPrice.doubleValue()))).flatMap(result -> Flux.from(result.records())
								.map(record -> mapToProduct(record.get("p").asMap()))),
				ReactiveSession::close)
				.doOnComplete(() -> log.info("Products retrieved for price range: {} - {}", minPrice, maxPrice));
	}

	@LogExecutionTime
	public Mono<Product> updateProduct(String id, Product product) {
		log.info("Updating product with id: {}", id);

		String cypher = """
				MATCH (p:Product {id: $id})
				SET p.name = $name,
				    p.description = $description,
				    p.price = $price,
				    p.quantity = $quantity,
				    p.category = $category,
				    p.updatedAt = $updatedAt
				RETURN p
				""";

		return Flux.usingWhen(
				Mono.just(driver.session(ReactiveSession.class, SessionConfig.builder().withDatabase("neo4j").build())),
				(ReactiveSession session) -> Flux.from(session.run(cypher, Values.parameters(
						"id", id,
						"name", product.getName(),
						"description", product.getDescription(),
						"price", product.getPrice().doubleValue(),
						"quantity", product.getQuantity(),
						"category", product.getCategory(),
						"updatedAt", LocalDateTime.now().toString()))).flatMap(result -> Flux.from(result.records())
								.map(record -> mapToProduct(record.get("p").asMap()))),
				ReactiveSession::close)
				.next()
				.doOnSuccess(updatedProduct -> log.info("Product updated successfully: {}", id))
				.doOnError(error -> log.error("Failed to update product: {}", error.getMessage()));
	}

	@LogExecutionTime
	public Mono<Void> deleteProduct(String id) {
		log.info("Deleting product with id: {}", id);

		String cypher = "MATCH (p:Product {id: $id}) DELETE p";

		return Mono.<Void, ReactiveSession>usingWhen(
				Mono.just(driver.session(ReactiveSession.class, SessionConfig.builder().withDatabase("neo4j").build())),
				session -> Mono.from(session.run(cypher, Values.parameters("id", id)))
						.flatMap(result -> Mono.from(result.consume())).then(),
				session -> Mono.from(session.close()))
				.doOnSuccess(v -> log.info("Product deleted successfully: {}", id))
				.doOnError(error -> log.error("Failed to delete product: {}", error.getMessage()));
	}

	private Product mapToProduct(Map<String, Object> map) {
		Product product = new Product();
		product.setId((String) map.get("id"));
		product.setName((String) map.get("name"));
		product.setDescription((String) map.get("description"));

		Object priceObj = map.get("price");
		if (priceObj instanceof Number) {
			product.setPrice(BigDecimal.valueOf(((Number) priceObj).doubleValue()));
		}

		Object quantityObj = map.get("quantity");
		if (quantityObj instanceof Number) {
			product.setQuantity(((Number) quantityObj).intValue());
		}

		product.setCategory((String) map.get("category"));
		product.setCreatedAt(LocalDateTime.parse((String) map.get("createdAt")));
		product.setUpdatedAt(LocalDateTime.parse((String) map.get("updatedAt")));
		product.setAvailable((Boolean) map.get("available"));
		return product;
	}
}
