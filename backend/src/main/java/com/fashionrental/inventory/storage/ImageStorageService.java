package com.fashionrental.inventory.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public interface ImageStorageService {

    UploadResult uploadImage(UUID itemId, InputStream inputStream, String originalFilename, long fileSize) throws IOException;

    void deleteImage(String fullUrl, String thumbnailUrl);
}
