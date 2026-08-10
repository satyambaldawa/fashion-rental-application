package com.fashionrental.inventory.storage;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LocalImageStorageServiceTest {

    private static final int FULL_MAX_PX = 800;
    private static final int THUMB_MAX_PX = 200;

    private final LocalImageStorageService service = new LocalImageStorageService();

    private InputStream tinyJpeg() throws IOException {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return new ByteArrayInputStream(out.toByteArray());
    }

    private void configureService() {
        ReflectionTestUtils.setField(service, "fullMaxPx", FULL_MAX_PX);
        ReflectionTestUtils.setField(service, "thumbMaxPx", THUMB_MAX_PX);
    }

    private void cleanUp(String namespace, UUID id, UploadResult result) throws IOException {
        service.deleteImage(result.fullUrl(), result.thumbnailUrl());
        Path dir = Paths.get("./uploads", namespace, id.toString());
        Files.deleteIfExists(dir);
    }

    @Test
    void should_namespace_uploaded_files_under_gallery_when_gallery_namespace_requested() throws IOException {
        configureService();
        UUID id = UUID.randomUUID();

        UploadResult result = service.uploadImage("gallery", id, tinyJpeg(), "photo.jpg", 1024);

        try {
            assertThat(result.fullUrl()).contains("/gallery/" + id + "/");
            assertThat(result.thumbnailUrl()).contains("/gallery/" + id + "/");
            assertThat(result.fullUrl()).doesNotContain("/items/");
        } finally {
            cleanUp("gallery", id, result);
        }
    }

    @Test
    void should_default_to_items_namespace_when_using_legacy_three_arg_overload() throws IOException {
        configureService();
        UUID itemId = UUID.randomUUID();

        UploadResult result = service.uploadImage(itemId, tinyJpeg(), "photo.jpg", 1024);

        try {
            assertThat(result.fullUrl()).contains("/items/" + itemId + "/");
            assertThat(result.thumbnailUrl()).contains("/items/" + itemId + "/");
            assertThat(result.fullUrl()).doesNotContain("/gallery/");
        } finally {
            cleanUp("items", itemId, result);
        }
    }
}
