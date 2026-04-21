package com.fashionrental.inventory.storage;

import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@Profile("dev")
public class LocalImageStorageService implements ImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalImageStorageService.class);

    private static final String BASE_DIR = "./uploads/items";
    private static final String BASE_URL = "http://localhost:8080/uploads/items";

    @Value("${app.storage.image.full-max-px}")
    private int fullMaxPx;

    @Value("${app.storage.image.thumb-max-px}")
    private int thumbMaxPx;

    @Override
    public UploadResult uploadImage(UUID itemId, InputStream inputStream, String originalFilename, long fileSize) throws IOException {
        Path itemDir = Paths.get(BASE_DIR, itemId.toString());
        Files.createDirectories(itemDir);

        String fileId = UUID.randomUUID().toString();
        Path fullPath = itemDir.resolve(fileId + "-full.jpg");
        Path thumbPath = itemDir.resolve(fileId + "-thumb.jpg");

        Thumbnails.of(inputStream)
                .size(fullMaxPx, fullMaxPx)
                .keepAspectRatio(true)
                .outputFormat("jpg")
                .toFile(fullPath.toFile());

        Thumbnails.of(fullPath.toFile())
                .size(thumbMaxPx, thumbMaxPx)
                .keepAspectRatio(true)
                .outputFormat("jpg")
                .toFile(thumbPath.toFile());

        String fullUrl = BASE_URL + "/" + itemId + "/" + fileId + "-full.jpg";
        String thumbnailUrl = BASE_URL + "/" + itemId + "/" + fileId + "-thumb.jpg";

        log.debug("Saved image for item {}: full={}, thumb={}", itemId, fullPath, thumbPath);
        return new UploadResult(fullUrl, thumbnailUrl);
    }

    @Override
    public void deleteImage(String fullUrl, String thumbnailUrl) {
        deleteFile(fullUrl);
        deleteFile(thumbnailUrl);
    }

    private void deleteFile(String url) {
        try {
            // URL format: http://localhost:8080/uploads/items/{itemId}/{filename}
            String path = URI.create(url).getPath();
            // path starts with /uploads/items/... — strip leading slash and resolve against cwd
            Path filePath = Paths.get("." + path);
            Files.deleteIfExists(filePath);
            log.debug("Deleted file: {}", filePath);
        } catch (Exception e) {
            log.warn("Failed to delete file at URL {}: {}", url, e.getMessage());
        }
    }
}
