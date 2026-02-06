package com.colligendis.server.controller;

import com.colligendis.server.model.Product;
import com.colligendis.server.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public Mono<ResponseEntity<Product>> createProduct(@RequestBody Product product) {
        log.info("Request to create product: {}", product.getName());
        
        return productService.createProduct(product)
                .map(createdProduct -> {
                    log.info("Product created successfully: {}", createdProduct.getId());
                    return ResponseEntity.status(HttpStatus.CREATED).body(createdProduct);
                })
                .onErrorResume(error -> {
                    log.error("Failed to create product: {}", error.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
                });
    }

    @GetMapping
    public Flux<Product> getAllProducts() {
        log.info("Request to get all products");
        return productService.findAllProducts()
                .doOnComplete(() -> log.info("All products retrieved successfully"));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Product>> getProductById(@PathVariable String id) {
        log.info("Request to get product by id: {}", id);
        
        return productService.findById(id)
                .map(product -> {
                    log.debug("Product found: {}", id);
                    return ResponseEntity.ok(product);
                })
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .doOnSuccess(response -> {
                    if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
                        log.warn("Product not found: {}", id);
                    }
                });
    }

    @GetMapping("/category/{category}")
    public Flux<Product> getProductsByCategory(@PathVariable String category) {
        log.info("Request to get products by category: {}", category);
        return productService.findByCategory(category)
                .doOnComplete(() -> log.info("Products retrieved for category: {}", category));
    }

    @GetMapping("/search")
    public Flux<Product> searchProducts(@RequestParam String term) {
        log.info("Request to search products with term: {}", term);
        return productService.searchProducts(term)
                .doOnComplete(() -> log.info("Product search completed"));
    }

    @GetMapping("/price-range")
    public Flux<Product> getProductsByPriceRange(
            @RequestParam BigDecimal minPrice,
            @RequestParam BigDecimal maxPrice) {
        log.info("Request to get products in price range: {} - {}", minPrice, maxPrice);
        return productService.findByPriceRange(minPrice, maxPrice)
                .doOnComplete(() -> log.info("Products retrieved for price range"));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<Product>> updateProduct(
            @PathVariable String id,
            @RequestBody Product product) {
        log.info("Request to update product: {}", id);
        
        return productService.updateProduct(id, product)
                .map(updatedProduct -> {
                    log.info("Product updated successfully: {}", id);
                    return ResponseEntity.ok(updatedProduct);
                })
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .onErrorResume(error -> {
                    log.error("Failed to update product: {}", error.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
                });
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteProduct(@PathVariable String id) {
        log.info("Request to delete product: {}", id);
        
        return productService.deleteProduct(id)
                .then(Mono.fromCallable(() -> {
                    log.info("Product deleted successfully: {}", id);
                    return ResponseEntity.noContent().<Void>build();
                }))
                .onErrorResume(error -> {
                    log.error("Failed to delete product: {}", error.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }
}
