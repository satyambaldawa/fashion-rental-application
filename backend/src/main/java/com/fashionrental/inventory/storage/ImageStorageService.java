package com.fashionrental.inventory.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public interface ImageStorageService {

    String ITEMS_NAMESPACE = "items";

    default UploadResult uploadImage(UUID itemId, InputStream inputStream, String originalFilename, long fileSize) throws IOException {
        return uploadImage(ITEMS_NAMESPACE, itemId, inputStream, originalFilename, fileSize);
    }

    UploadResult uploadImage(String namespace, UUID id, InputStream inputStream, String originalFilename, long fileSize) throws IOException;

    void deleteImage(String fullUrl, String thumbnailUrl);

    UploadResult copyImage(UUID newItemId, String sourceFullUrl, String sourceThumbnailUrl) throws IOException;
}
