package com.hmdp.service.storage;

import com.hmdp.config.BlogImageProperties;
import com.hmdp.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlogImageStorageTest {

    @TempDir
    Path uploadRoot;

    private BlogImageStorage storage;

    @BeforeEach
    void setUp() {
        BlogImageProperties properties = new BlogImageProperties();
        properties.setRoot(uploadRoot.toString());
        properties.setPublicPrefix("/imgs/");
        storage = new BlogImageStorage(properties);
    }

    @Test
    void store_should_detect_image_content_and_generate_safe_path() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.png",
                "application/octet-stream",
                createPng(20, 10)
        );

        StoredBlogImage stored = storage.store(file);

        assertEquals("image/png", stored.getContentType());
        assertEquals(20, stored.getWidth());
        assertEquals(10, stored.getHeight());
        assertTrue(stored.getPublicUrl().startsWith("/imgs/blogs/"));
        assertTrue(Files.isRegularFile(uploadRoot.resolve(stored.getStorageKey())));

        storage.delete(stored.getStorageKey());
        assertFalse(Files.exists(uploadRoot.resolve(stored.getStorageKey())));
    }

    @Test
    void store_should_reject_extension_that_does_not_match_content() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.jpg",
                "image/jpeg",
                createPng(10, 10)
        );

        assertThrows(BusinessException.class, () -> storage.store(file));
    }

    @Test
    void resolveWithinUploadRoot_should_reject_path_traversal() {
        assertThrows(
                BusinessException.class,
                () -> BlogImageStorage.resolveWithinUploadRoot(uploadRoot, "../../outside.jpg")
        );
    }

    private byte[] createPng(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
