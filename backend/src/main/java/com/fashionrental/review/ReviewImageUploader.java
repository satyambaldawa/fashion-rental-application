package com.fashionrental.review;

import com.fashionrental.common.exception.ValidationException;
import com.fashionrental.inventory.storage.ImageStorageService;
import com.fashionrental.inventory.storage.UploadResult;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class ReviewImageUploader {

    private static final String REVIEWS_NAMESPACE = "reviews";
    private static final int MAX_IMAGES = 3;
    private static final long MAX_BYTES_PER_IMAGE = 5L * 1024 * 1024;
    private static final List<String> ALLOWED_CONTENT_TYPES =
            List.of("image/jpeg", "image/png", "image/webp");

    private final ImageStorageService imageStorageService;

    public ReviewImageUploader(ImageStorageService imageStorageService) {
        this.imageStorageService = imageStorageService;
    }

    /**
     * Contract warning: this is not transactional. If the caller's transaction later fails it
     * MUST call {@link #deleteAll} with the returned results, or the stored objects are orphaned.
     */
    public List<UploadResult> uploadAll(MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return List.of();
        }
        validateAll(files);

        List<UploadResult> uploaded = new ArrayList<>();
        for (MultipartFile file : files) {
            try {
                uploaded.add(imageStorageService.uploadImage(
                        REVIEWS_NAMESPACE,
                        UUID.randomUUID(),
                        file.getInputStream(),
                        file.getOriginalFilename(),
                        file.getSize()));
            } catch (IOException | RuntimeException e) {
                deleteAll(uploaded);
                throw new IllegalStateException("Failed to store review image", e);
            }
        }
        return uploaded;
    }

    public void deleteAll(List<UploadResult> uploaded) {
        uploaded.forEach(result -> imageStorageService.deleteImage(result.fullUrl(), result.thumbnailUrl()));
    }

    private void validateAll(MultipartFile[] files) {
        if (files.length > MAX_IMAGES) {
            throw new ValidationException("You can attach at most 3 photos");
        }
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                throw new ValidationException("Photo is empty");
            }
            if (file.getSize() > MAX_BYTES_PER_IMAGE) {
                throw new ValidationException("Each photo must be smaller than 5MB");
            }
            if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
                throw new ValidationException("Only JPEG, PNG, and WebP photos are allowed");
            }
        }
    }
}
