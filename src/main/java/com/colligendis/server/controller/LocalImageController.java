package com.colligendis.server.controller;

import com.colligendis.server.service.LocalImageFileService;
import com.colligendis.server.service.LocalImageFileService.ImageSize;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Single public endpoint for serving images stored on the server filesystem.
 */
@Slf4j
@RestController
@RequestMapping("/api/public/images")
@RequiredArgsConstructor
public class LocalImageController {

	private final LocalImageFileService localImageFileService;

	@GetMapping("/local")
	public Mono<ResponseEntity<Resource>> getLocalImage(
			@RequestParam(name = "path") String path,
			@RequestParam(name = "size", required = false) String size) {
		if (path == null || path.isBlank()) {
			return Mono.just(ResponseEntity.badRequest().build());
		}

		final ImageSize requestedSize = "SMALL".equalsIgnoreCase(size) ? ImageSize.SMALL : ImageSize.MAIN;

		return Mono.fromCallable(() -> localImageFileService.resolveAllowedFile(path, requestedSize))
				.subscribeOn(Schedulers.boundedElastic())
				.flatMap(resolved -> {
					if (resolved == null) {
						return Mono.<ResponseEntity<Resource>>just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
					}
					return toImageResponse(resolved);
				})
				.onErrorResume(error -> {
					log.error("Failed to serve local image path={}", path, error);
					return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).<Resource>build());
				});
	}

	private Mono<ResponseEntity<Resource>> toImageResponse(Path filePath) {
		final Resource resource = new FileSystemResource(filePath);
		final MediaType mediaType = localImageFileService.probeMediaType(filePath);
		return Mono.just(ResponseEntity.ok()
				.contentType(mediaType)
				.cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filePath.getFileName() + "\"")
				.body(resource));
	}
}
