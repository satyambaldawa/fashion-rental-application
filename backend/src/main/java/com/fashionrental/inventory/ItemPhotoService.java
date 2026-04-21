package com.fashionrental.inventory;

import com.fashionrental.common.exception.ResourceNotFoundException;
import com.fashionrental.common.exception.ValidationException;
import com.fashionrental.inventory.model.request.ReorderPhotosRequest;
import com.fashionrental.inventory.model.response.ItemPhotoResponse;
import com.fashionrental.inventory.storage.ImageStorageService;
import com.fashionrental.inventory.storage.UploadResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ItemPhotoService {

    private static final List<String> ALLOWED_CONTENT_TYPES = List.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private final ItemRepository itemRepository;
    private final ItemPhotoRepository itemPhotoRepository;
    private final ImageStorageService imageStorageService;

    @Value("${app.storage.image.max-upload-bytes}")
    private long maxUploadBytes;

    @Value("${app.storage.image.max-photos-per-item}")
    private int maxPhotosPerItem;

    public ItemPhotoService(ItemRepository itemRepository,
                            ItemPhotoRepository itemPhotoRepository,
                            ImageStorageService imageStorageService) {
        this.itemRepository = itemRepository;
        this.itemPhotoRepository = itemPhotoRepository;
        this.imageStorageService = imageStorageService;
    }

    public ItemPhotoResponse uploadPhoto(UUID itemId, MultipartFile file) {
        itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + itemId));

        long currentCount = itemPhotoRepository.countByItemId(itemId);
        if (currentCount >= maxPhotosPerItem) {
            throw new ValidationException("Maximum of " + maxPhotosPerItem + " photos allowed per item");
        }

        if (file.getSize() > maxUploadBytes) {
            throw new ValidationException("File size exceeds maximum allowed size");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ValidationException("Invalid file type. Only JPEG, PNG, WebP, and GIF are allowed");
        }

        UploadResult uploadResult;
        try {
            uploadResult = imageStorageService.uploadImage(
                    itemId,
                    file.getInputStream(),
                    file.getOriginalFilename(),
                    file.getSize()
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload image", e);
        }

        Item item = itemRepository.getReferenceById(itemId);
        ItemPhoto photo = new ItemPhoto();
        photo.setItem(item);
        photo.setUrl(uploadResult.fullUrl());
        photo.setThumbnailUrl(uploadResult.thumbnailUrl());
        photo.setSortOrder((int) currentCount);

        ItemPhoto saved = itemPhotoRepository.save(photo);
        return new ItemPhotoResponse(saved.getId(), saved.getUrl(), saved.getThumbnailUrl(), saved.getSortOrder());
    }

    public void deletePhoto(UUID itemId, UUID photoId) {
        ItemPhoto photo = itemPhotoRepository.findByIdAndItemId(photoId, itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo not found: " + photoId));

        imageStorageService.deleteImage(photo.getUrl(), photo.getThumbnailUrl());
        itemPhotoRepository.delete(photo);
    }

    public void reorderPhotos(UUID itemId, ReorderPhotosRequest request) {
        request.photos().forEach(orderItem ->
                itemPhotoRepository.updateSortOrder(orderItem.id(), orderItem.sortOrder())
        );
    }
}
