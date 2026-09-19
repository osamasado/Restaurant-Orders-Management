package org.restaurantordersmanagement.backend.menu.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stores meal/raw-material images on the local filesystem, under
 * {@code app.upload.dir}, and serves them back via the resource handler
 * registered in {@link WebConfig}. Shared by future controllers for both
 * entity types (issues #13, #14) rather than duplicated per entity.
 */
@Service
public class ImageStorageService {

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;

    private static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    private final Path uploadDir;

    public ImageStorageService(@Value("${app.upload.dir}") String uploadDir) {
        this.uploadDir = Path.of(uploadDir);
    }

    /**
     * @param subdirectory logical grouping for the stored file, e.g. "meals" or "raw-materials"
     * @return the path (relative to {@code app.upload.dir}) to persist on the entity, e.g. "meals/&lt;uuid&gt;.jpg"
     */
    public String store(MultipartFile file, String subdirectory) {
        if (file == null || file.isEmpty()) {
            throw new InvalidImageException("No image file provided");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new InvalidImageException("Image exceeds the maximum allowed size of 5MB");
        }

        String extension = ALLOWED_CONTENT_TYPES.get(file.getContentType());
        if (extension == null) {
            throw new InvalidImageException("Unsupported image type: " + file.getContentType()
                    + ". Allowed types: " + Set.copyOf(ALLOWED_CONTENT_TYPES.keySet()));
        }

        try {
            Path targetDir = uploadDir.resolve(subdirectory);
            Files.createDirectories(targetDir);

            String filename = UUID.randomUUID() + extension;
            Path targetFile = targetDir.resolve(filename);
            file.transferTo(targetFile);

            return subdirectory + "/" + filename;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store image", e);
        }
    }

    /**
     * Removes a previously stored image. No-op if {@code imagePath} is null/blank
     * or the file no longer exists.
     */
    public void delete(String imagePath) {
        if (!StringUtils.hasText(imagePath)) {
            return;
        }
        try {
            Files.deleteIfExists(uploadDir.resolve(imagePath).normalize());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete image: " + imagePath, e);
        }
    }

}
