package com.colligendis.server.controller;

import com.colligendis.server.model.User;
import com.colligendis.server.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public Mono<ResponseEntity<User>> createUser(@RequestBody User user) {
        log.info("Request to create user: {}", user.getUsername());
        
        return userService.createUser(user)
                .map(createdUser -> {
                    log.info("User created successfully: {}", createdUser.getId());
                    return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
                })
                .onErrorResume(error -> {
                    log.error("Failed to create user: {}", error.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
                });
    }

    @GetMapping
    public Flux<User> getAllUsers() {
        log.info("Request to get all users");
        return userService.findAllUsers()
                .doOnComplete(() -> log.info("All users retrieved successfully"));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<User>> getUserById(@PathVariable String id) {
        log.info("Request to get user by id: {}", id);
        
        return userService.findById(id)
                .map(user -> {
                    log.debug("User found: {}", id);
                    return ResponseEntity.ok(user);
                })
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .doOnSuccess(response -> {
                    if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
                        log.warn("User not found: {}", id);
                    }
                });
    }

    @GetMapping("/username/{username}")
    public Mono<ResponseEntity<User>> getUserByUsername(@PathVariable String username) {
        log.info("Request to get user by username: {}", username);
        
        return userService.findByUsername(username)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<User>> updateUser(
            @PathVariable String id, 
            @RequestBody User user) {
        log.info("Request to update user: {}", id);
        
        return userService.updateUser(id, user)
                .map(updatedUser -> {
                    log.info("User updated successfully: {}", id);
                    return ResponseEntity.ok(updatedUser);
                })
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .onErrorResume(error -> {
                    log.error("Failed to update user: {}", error.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
                });
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteUser(@PathVariable String id) {
        log.info("Request to delete user: {}", id);
        
        return userService.deleteUser(id)
                .then(Mono.fromCallable(() -> {
                    log.info("User deleted successfully: {}", id);
                    return ResponseEntity.noContent().<Void>build();
                }))
                .onErrorResume(error -> {
                    log.error("Failed to delete user: {}", error.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }
}
