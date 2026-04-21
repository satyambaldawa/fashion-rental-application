package com.fashionrental.inventory;

import com.fashionrental.common.exception.ResourceNotFoundException;
import com.fashionrental.common.exception.ValidationException;
import com.fashionrental.inventory.model.request.PhotoOrderItem;
import com.fashionrental.inventory.model.request.ReorderPhotosRequest;
import com.fashionrental.inventory.model.response.ItemPhotoResponse;
import com.fashionrental.inventory.storage.ImageStorageService;
import com.fashionrental.inventory.storage.UploadResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ItemPhotoServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private ItemPhotoRepository itemPhotoRepository;

    @Mock
    private ImageStorageService imageStorageService;

    @InjectMocks
    private ItemPhotoService itemPhotoService;

    private static final long MAX_BYTES = 15 * 1024 * 1024L; // 15 MB
    private static final int MAX_PHOTOS = 8;

    private void configureService() {
        ReflectionTestUtils.setField(itemPhotoService, "maxUploadBytes", MAX_BYTES);
        ReflectionTestUtils.setField(itemPhotoService, "maxPhotosPerItem", MAX_PHOTOS);
    }

    private Item activeItem(UUID id) {
        Item item = new Item();
        item.setName("Test Item");
        item.setIsActive(true);
        return item;
    }

    private MockMultipartFile validJpeg(String name) {
        return new MockMultipartFile(name, name + ".jpg", "image/jpeg", new byte[1024]);
    }

    private ItemPhoto savedPhoto(UUID itemId, String url, String thumbUrl, int sortOrder) {
        Item item = new Item();
        ItemPhoto photo = new ItemPhoto();
        photo.setItem(item);
        photo.setUrl(url);
        photo.setThumbnailUrl(thumbUrl);
        photo.setSortOrder(sortOrder);
        return photo;
    }

    // ─── uploadPhoto ─────────────────────────────────────────────────────────

    @Test
    void should_upload_photo_and_return_response_with_urls() throws IOException {
        configureService();
        UUID itemId = UUID.randomUUID();
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(activeItem(itemId)));
        when(itemPhotoRepository.countByItemId(itemId)).thenReturn(0L);
        when(imageStorageService.uploadImage(any(), any(), any(), anyLong()))
                .thenReturn(new UploadResult("https://cdn/full.jpg", "https://cdn/thumb.jpg"));

        ItemPhoto savedPhoto = savedPhoto(itemId, "https://cdn/full.jpg", "https://cdn/thumb.jpg", 0);
        when(itemPhotoRepository.save(any(ItemPhoto.class))).thenReturn(savedPhoto);
        when(itemRepository.getReferenceById(itemId)).thenReturn(activeItem(itemId));

        MockMultipartFile file = validJpeg("photo");
        ItemPhotoResponse result = itemPhotoService.uploadPhoto(itemId, file);

        assertThat(result.url()).isEqualTo("https://cdn/full.jpg");
        assertThat(result.thumbnailUrl()).isEqualTo("https://cdn/thumb.jpg");
        assertThat(result.sortOrder()).isZero();
    }

    @Test
    void should_set_sort_order_to_current_photo_count_on_upload() throws IOException {
        configureService();
        UUID itemId = UUID.randomUUID();
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(activeItem(itemId)));
        when(itemPhotoRepository.countByItemId(itemId)).thenReturn(3L); // already 3 photos
        when(imageStorageService.uploadImage(any(), any(), any(), anyLong()))
                .thenReturn(new UploadResult("https://cdn/full4.jpg", "https://cdn/thumb4.jpg"));

        ItemPhoto savedPhoto = savedPhoto(itemId, "https://cdn/full4.jpg", "https://cdn/thumb4.jpg", 3);
        when(itemPhotoRepository.save(any(ItemPhoto.class))).thenReturn(savedPhoto);
        when(itemRepository.getReferenceById(itemId)).thenReturn(activeItem(itemId));

        ItemPhotoResponse result = itemPhotoService.uploadPhoto(itemId, validJpeg("photo4"));

        assertThat(result.sortOrder()).isEqualTo(3);
    }

    @Test
    void should_throw_validation_exception_when_already_at_max_photos() {
        configureService();
        UUID itemId = UUID.randomUUID();
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(activeItem(itemId)));
        when(itemPhotoRepository.countByItemId(itemId)).thenReturn(8L);

        assertThatThrownBy(() -> itemPhotoService.uploadPhoto(itemId, validJpeg("extra")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("8");
    }

    @Test
    void should_throw_validation_exception_when_file_exceeds_size_limit() {
        configureService();
        UUID itemId = UUID.randomUUID();
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(activeItem(itemId)));
        when(itemPhotoRepository.countByItemId(itemId)).thenReturn(0L);

        // 16 MB — over the 15 MB limit
        byte[] oversizedContent = new byte[(int) (MAX_BYTES + 1)];
        MockMultipartFile oversized = new MockMultipartFile("file", "big.jpg", "image/jpeg", oversizedContent);

        assertThatThrownBy(() -> itemPhotoService.uploadPhoto(itemId, oversized))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("size");
    }

    @Test
    void should_throw_validation_exception_when_file_is_not_an_image() {
        configureService();
        UUID itemId = UUID.randomUUID();
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(activeItem(itemId)));
        when(itemPhotoRepository.countByItemId(itemId)).thenReturn(0L);

        MockMultipartFile pdfFile = new MockMultipartFile(
                "file", "document.pdf", "application/pdf", new byte[1024]
        );

        assertThatThrownBy(() -> itemPhotoService.uploadPhoto(itemId, pdfFile))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid file type");
    }

    @Test
    void should_throw_not_found_when_item_does_not_exist_for_upload() {
        configureService();
        UUID itemId = UUID.randomUUID();
        when(itemRepository.findById(itemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemPhotoService.uploadPhoto(itemId, validJpeg("photo")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Item not found");
    }

    @Test
    void should_accept_png_and_webp_content_types() throws IOException {
        configureService();
        UUID itemId = UUID.randomUUID();
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(activeItem(itemId)));
        when(itemPhotoRepository.countByItemId(itemId)).thenReturn(0L);
        when(imageStorageService.uploadImage(any(), any(), any(), anyLong()))
                .thenReturn(new UploadResult("https://cdn/full.webp", "https://cdn/thumb.webp"));
        ItemPhoto savedPhoto = savedPhoto(itemId, "https://cdn/full.webp", "https://cdn/thumb.webp", 0);
        when(itemPhotoRepository.save(any())).thenReturn(savedPhoto);
        when(itemRepository.getReferenceById(itemId)).thenReturn(activeItem(itemId));

        MockMultipartFile webpFile = new MockMultipartFile("file", "image.webp", "image/webp", new byte[512]);
        ItemPhotoResponse result = itemPhotoService.uploadPhoto(itemId, webpFile);

        assertThat(result.url()).contains("webp");
    }

    // ─── deletePhoto ──────────────────────────────────────────────────────────

    @Test
    void should_delete_photo_from_storage_and_repository() {
        configureService();
        UUID itemId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();

        ItemPhoto photo = savedPhoto(itemId, "https://cdn/full.jpg", "https://cdn/thumb.jpg", 0);
        when(itemPhotoRepository.findByIdAndItemId(photoId, itemId)).thenReturn(Optional.of(photo));

        itemPhotoService.deletePhoto(itemId, photoId);

        verify(imageStorageService).deleteImage("https://cdn/full.jpg", "https://cdn/thumb.jpg");
        verify(itemPhotoRepository).delete(photo);
    }

    @Test
    void should_throw_not_found_when_photo_does_not_belong_to_item() {
        configureService();
        UUID itemId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        when(itemPhotoRepository.findByIdAndItemId(photoId, itemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemPhotoService.deletePhoto(itemId, photoId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Photo not found");
    }

    // ─── reorderPhotos ────────────────────────────────────────────────────────

    @Test
    void should_call_update_sort_order_for_each_item_in_request() {
        configureService();
        UUID itemId = UUID.randomUUID();
        UUID photoId1 = UUID.randomUUID();
        UUID photoId2 = UUID.randomUUID();

        ReorderPhotosRequest request = new ReorderPhotosRequest(List.of(
                new PhotoOrderItem(photoId1, 0),
                new PhotoOrderItem(photoId2, 1)
        ));

        itemPhotoService.reorderPhotos(itemId, request);

        verify(itemPhotoRepository).updateSortOrder(photoId1, 0);
        verify(itemPhotoRepository).updateSortOrder(photoId2, 1);
    }

    @Test
    void should_handle_empty_reorder_list_without_error() {
        configureService();
        UUID itemId = UUID.randomUUID();
        ReorderPhotosRequest request = new ReorderPhotosRequest(List.of());

        itemPhotoService.reorderPhotos(itemId, request);

        verifyNoInteractions(itemPhotoRepository);
    }
}
