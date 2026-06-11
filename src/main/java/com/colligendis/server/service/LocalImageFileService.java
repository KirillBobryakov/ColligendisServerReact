package com.colligendis.server.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.imageio.ImageIO;

@Slf4j
@Service
public class LocalImageFileService {

	public enum ImageSize {
		MAIN,
		SMALL
	}

	private static final int MAX_SMALL_IMAGE_SIZE_PX = 300;

	private static final String DEFAULT_NTYPE_IMAGES_ROOT = "/Users/kirillbobryakov/Coins/Numista/storage/images/ntypes";
	private static final String DEFAULT_SIGNATURES_ROOT = "/Users/kirillbobryakov/Coins/Numista/storage/signatures";

	@Value("${numista.images.ntypes.storage-root:" + DEFAULT_NTYPE_IMAGES_ROOT + "}")
	private String ntypeImagesStorageRoot;

	@Value("${numista.images.signatures.storage-root:" + DEFAULT_SIGNATURES_ROOT + "}")
	private String signaturesStorageRoot;

	public Path resolveAllowedFile(String rawPath, ImageSize size) {
		final Path mainPath = resolveAllowedMainFile(rawPath);
		if (mainPath == null) {
			return null;
		}
		if (size == ImageSize.MAIN) {
			return mainPath;
		}
		return resolveSmallVariant(mainPath);
	}

	public Path resolveAllowedMainFile(String rawPath) {
		if (rawPath == null || rawPath.isBlank()) {
			return null;
		}
		final Path candidate = Paths.get(rawPath.trim()).normalize().toAbsolutePath();
		if (!Files.exists(candidate) || !Files.isRegularFile(candidate)) {
			return null;
		}
		for (Path allowedRoot : allowedRoots()) {
			final Path normalizedRoot = allowedRoot.normalize().toAbsolutePath();
			if (candidate.startsWith(normalizedRoot)) {
				return candidate;
			}
		}
		log.warn("Rejected local image request outside allowed roots: {}", candidate);
		return null;
	}

	public MediaType probeMediaType(Path file) {
		final String name = file.getFileName().toString().toLowerCase();
		if (name.endsWith(".png")) {
			return MediaType.IMAGE_PNG;
		}
		if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
			return MediaType.IMAGE_JPEG;
		}
		if (name.endsWith(".gif")) {
			return MediaType.IMAGE_GIF;
		}
		if (name.endsWith(".webp")) {
			return MediaType.parseMediaType("image/webp");
		}
		return MediaType.APPLICATION_OCTET_STREAM;
	}

	private Path resolveSmallVariant(Path mainImagePath) {
		final Path smallPath = buildSmallVariantPath(mainImagePath);
		try {
			if (Files.exists(smallPath) && Files.isRegularFile(smallPath)) {
				return smallPath;
			}
			final BufferedImage originalImage = ImageIO.read(mainImagePath.toFile());
			if (originalImage == null || !saveSmallVariantFromBufferedImage(mainImagePath, originalImage)) {
				return mainImagePath;
			}
			if (Files.exists(smallPath) && Files.isRegularFile(smallPath)) {
				return smallPath;
			}
		} catch (Exception error) {
			log.warn("Failed to resolve small variant for {}", mainImagePath, error);
		}
		return mainImagePath;
	}

	private Path buildSmallVariantPath(Path originalPath) {
		final String fileName = originalPath.getFileName().toString();
		final int dotIndex = fileName.lastIndexOf('.');
		final String smallFileName = dotIndex > 0
				? fileName.substring(0, dotIndex) + "_small" + fileName.substring(dotIndex)
				: fileName + "_small.jpg";
		return originalPath.getParent().resolve(smallFileName).normalize();
	}

	private boolean saveSmallVariantFromBufferedImage(Path originalPath, BufferedImage originalImage) throws Exception {
		final int originalWidth = originalImage.getWidth();
		final int originalHeight = originalImage.getHeight();
		if (originalWidth <= 0 || originalHeight <= 0) {
			return false;
		}

		final double scale = Math.min(
				1.0d,
				Math.min(
						(double) MAX_SMALL_IMAGE_SIZE_PX / originalWidth,
						(double) MAX_SMALL_IMAGE_SIZE_PX / originalHeight));
		final int targetWidth = Math.max(1, (int) Math.round(originalWidth * scale));
		final int targetHeight = Math.max(1, (int) Math.round(originalHeight * scale));

		final BufferedImage smallImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
		final Graphics2D graphics = smallImage.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
		} finally {
			graphics.dispose();
		}

		final Path smallPath = buildSmallVariantPath(originalPath);
		Files.createDirectories(smallPath.getParent());
		return ImageIO.write(smallImage, "jpg", smallPath.toFile());
	}

	private List<Path> allowedRoots() {
		return List.of(
				Paths.get(ntypeImagesStorageRoot),
				Paths.get(signaturesStorageRoot));
	}
}
