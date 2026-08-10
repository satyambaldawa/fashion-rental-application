package com.fashionrental.gallery;

import com.fashionrental.common.exception.ResourceNotFoundException;
import com.fashionrental.common.exception.ValidationException;
import com.fashionrental.gallery.model.request.UpdateGalleryImageRequest;
import com.fashionrental.gallery.model.response.GalleryImageResponse;
import com.fashionrental.inventory.Item;
import com.fashionrental.inventory.storage.ImageStorageService;
import com.fashionrental.inventory.storage.UploadResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class GalleryService {

    private static final String GALLERY_NAMESPACE = "gallery";
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private final GalleryImageRepository galleryImageRepository;
    private final ImageStorageService imageStorageService;

    public GalleryService(GalleryImageRepository galleryImageRepository, ImageStorageService imageStorageService) {
        this.galleryImageRepository = galleryImageRepository;
        this.imageStorageService = imageStorageService;
    }

    @Transactional(readOnly = true)
    public List<GalleryImageResponse> listPublic(Item.Category category) {
        List<GalleryImage> images = category == null
                ? galleryImageRepository.findByIsActiveTrueOrderByCreatedAtDescIdDesc()
                : galleryImageRepository.findByCategoryAndIsActiveTrueOrderByCreatedAtDescIdDesc(category);
        return images.stream().map(GalleryService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<GalleryImageResponse> listAdmin(Item.Category category) {
        List<GalleryImage> images = category == null
                ? galleryImageRepository.findAllByOrderByCreatedAtDescIdDesc()
                : galleryImageRepository.findByCategoryOrderByCreatedAtDescIdDesc(category);
        return images.stream().map(GalleryService::toResponse).toList();
    }

    public List<GalleryImageResponse> upload(Item.Category category, MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new ValidationException("At least one file is required");
        }

        List<GalleryImageResponse> responses = new ArrayList<>();
        for (MultipartFile file : files) {
            String contentType = file.getContentType();
            if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
                throw new ValidationException("Invalid file type. Only JPEG, PNG, WebP, and GIF are allowed");
            }

            UploadResult uploadResult;
            try {
                uploadResult = imageStorageService.uploadImage(
                        GALLERY_NAMESPACE,
                        UUID.randomUUID(),
                        file.getInputStream(),
                        file.getOriginalFilename(),
                        file.getSize()
                );
            } catch (IOException e) {
                throw new RuntimeException("Failed to upload image", e);
            }

            GalleryImage image = new GalleryImage();
            image.setCategory(category);
            image.setImageUrl(uploadResult.fullUrl());
            image.setThumbnailUrl(uploadResult.thumbnailUrl());
            image.setIsActive(true);
            image.setCaption(null);

            GalleryImage saved = galleryImageRepository.save(image);
            responses.add(toResponse(saved));
        }

        return responses;
    }

    public GalleryImageResponse update(UUID id, UpdateGalleryImageRequest request) {
        GalleryImage image = galleryImageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Gallery image not found: " + id));

        if (request.caption() != null) {
            image.setCaption(request.caption());
        }
        if (request.isActive() != null) {
            image.setIsActive(request.isActive());
        }

        GalleryImage saved = galleryImageRepository.save(image);
        return toResponse(saved);
    }

    public void delete(UUID id) {
        GalleryImage image = galleryImageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Gallery image not found: " + id));

        imageStorageService.deleteImage(image.getImageUrl(), image.getThumbnailUrl());
        galleryImageRepository.delete(image);
    }

    private static GalleryImageResponse toResponse(GalleryImage image) {
        return new GalleryImageResponse(
                image.getId(),
                image.getCategory(),
                image.getImageUrl(),
                image.getThumbnailUrl(),
                image.getCaption(),
                image.getIsActive()
        );
    }
}
