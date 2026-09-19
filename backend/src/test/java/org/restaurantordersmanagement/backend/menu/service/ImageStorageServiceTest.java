package org.restaurantordersmanagement.backend.menu.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class ImageStorageServiceTest {

    @TempDir
    Path uploadDir;

    private ImageStorageService newService() {
        return new ImageStorageService(uploadDir.toString());
    }

    @Test
    void storesValidImageAndReturnsResolvablePath() {
        MockMultipartFile file = new MockMultipartFile("image", "dish.jpg", "image/jpeg", "fake-jpeg-bytes".getBytes());

        String storedPath = newService().store(file, "meals");

        assertTrue(storedPath.startsWith("meals/"));
        assertTrue(storedPath.endsWith(".jpg"));
        assertTrue(Files.exists(uploadDir.resolve(storedPath)));
    }

    @Test
    void rejectsOversizedFile() throws IOException {
        byte[] tooLarge = new byte[6 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("image", "dish.jpg", "image/jpeg", tooLarge);

        assertThrows(InvalidImageException.class, () -> newService().store(file, "meals"));
    }

    @Test
    void rejectsUnsupportedContentType() {
        MockMultipartFile file = new MockMultipartFile("image", "dish.gif", "image/gif", "fake-gif-bytes".getBytes());

        assertThrows(InvalidImageException.class, () -> newService().store(file, "meals"));
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("image", "empty.jpg", "image/jpeg", new byte[0]);

        assertThrows(InvalidImageException.class, () -> newService().store(file, "meals"));
    }

    @Test
    void deleteRemovesExistingFileAndNoOpsOnNull() {
        MockMultipartFile file = new MockMultipartFile("image", "dish.png", "image/png", "fake-png-bytes".getBytes());
        ImageStorageService service = newService();
        String storedPath = service.store(file, "raw-materials");

        service.delete(storedPath);

        assertFalse(Files.exists(uploadDir.resolve(storedPath)));
        service.delete(null);
    }

}
