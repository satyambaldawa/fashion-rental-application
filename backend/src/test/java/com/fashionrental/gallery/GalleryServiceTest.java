package com.fashionrental.gallery;

import com.fashionrental.common.exception.ResourceNotFoundException;
import com.fashionrental.common.exception.ValidationException;
import com.fashionrental.gallery.model.request.UpdateGalleryImageRequest;
import com.fashionrental.gallery.model.response.GalleryImageResponse;
import com.fashionrental.inventory.Item;
import com.fashionrental.inventory.storage.ImageStorageService;
import com.fashionrental.inventory.storage.UploadResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GalleryServiceTest {

    @Mock
    private GalleryImageRepository galleryImageRepository;

    @Mock
    private ImageStorageService imageStorageService;

    @InjectMocks
    private GalleryService galleryService;

    private MockMultipartFile validJpeg(String name) {
        return new MockMultipartFile(name, name + ".jpg", "image/jpeg", new byte[1024]);
    }

    private GalleryImage savedImage(Item.Category category, String url, String thumbUrl) {
        GalleryImage image = new GalleryImage();
        image.setCategory(category);
        image.setImageUrl(url);
        image.setThumbnailUrl(thumbUrl);
        image.setIsActive(true);
        return image;
    }

    // ─── upload ──────────────────────────────────────────────────────────────

    @Test
    void should_upload_and_return_responses_with_urls_and_active_true() throws IOException {
        Item.Category category = Item.Category.COSTUME;
        when(imageStorageService.uploadImage(eq("gallery"), any(), any(), any(), anyLong()))
                .thenReturn(new UploadResult("https://cdn/full.jpg", "https://cdn/thumb.jpg"));
        when(galleryImageRepository.save(any(GalleryImage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<GalleryImageResponse> result = galleryService.upload(category, new MultipartFile[]{validJpeg("photo")});

        assertThat(result).hasSize(1);
        assertThat(result.get(0).imageUrl()).isEqualTo("https://cdn/full.jpg");
        assertThat(result.get(0).thumbnailUrl()).isEqualTo("https://cdn/thumb.jpg");
        assertThat(result.get(0).isActive()).isTrue();
    }

    @Test
    void should_upload_every_file_in_a_multi_file_batch() throws IOException {
        Item.Category category = Item.Category.COSTUME;
        when(imageStorageService.uploadImage(eq("gallery"), any(), any(), any(), anyLong()))
                .thenReturn(new UploadResult("https://cdn/full.jpg", "https://cdn/thumb.jpg"));
        when(galleryImageRepository.save(any(GalleryImage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MultipartFile[] files = {validJpeg("a"), validJpeg("b"), validJpeg("c")};
        List<GalleryImageResponse> result = galleryService.upload(category, files);

        assertThat(result).hasSize(3);
        verify(galleryImageRepository, times(3)).save(any(GalleryImage.class));
    }

    @Test
    void should_throw_validation_exception_when_file_is_not_an_image() {
        Item.Category category = Item.Category.COSTUME;
        MockMultipartFile pdfFile = new MockMultipartFile("file", "document.pdf", "application/pdf", new byte[1024]);

        assertThatThrownBy(() -> galleryService.upload(category, new MultipartFile[]{pdfFile}))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid file type");
    }

    @Test
    void should_throw_validation_exception_when_files_array_is_empty() {
        assertThatThrownBy(() -> galleryService.upload(Item.Category.COSTUME, new MultipartFile[]{}))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(galleryImageRepository, imageStorageService);
    }

    // ─── update ──────────────────────────────────────────────────────────────

    @Test
    void should_apply_non_null_fields_and_leave_omitted_fields_unchanged() {
        UUID id = UUID.randomUUID();
        GalleryImage existing = savedImage(Item.Category.COSTUME, "https://cdn/full.jpg", "https://cdn/thumb.jpg");
        existing.setCaption("Original caption");
        when(galleryImageRepository.findById(id)).thenReturn(Optional.of(existing));
        when(galleryImageRepository.save(any(GalleryImage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateGalleryImageRequest request = new UpdateGalleryImageRequest(null, false);
        GalleryImageResponse result = galleryService.update(id, request);

        assertThat(result.caption()).isEqualTo("Original caption");
        assertThat(result.isActive()).isFalse();
    }

    @Test
    void should_update_caption_and_active_state() {
        UUID id = UUID.randomUUID();
        GalleryImage existing = savedImage(Item.Category.COSTUME, "https://cdn/full.jpg", "https://cdn/thumb.jpg");
        when(galleryImageRepository.findById(id)).thenReturn(Optional.of(existing));
        when(galleryImageRepository.save(any(GalleryImage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateGalleryImageRequest request = new UpdateGalleryImageRequest("New caption", false);
        GalleryImageResponse result = galleryService.update(id, request);

        assertThat(result.caption()).isEqualTo("New caption");
        assertThat(result.isActive()).isFalse();
    }

    @Test
    void should_throw_not_found_when_updating_unknown_id() {
        UUID id = UUID.randomUUID();
        when(galleryImageRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> galleryService.update(id, new UpdateGalleryImageRequest(null, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Gallery image not found");
    }

    // ─── delete ──────────────────────────────────────────────────────────────

    @Test
    void should_delete_image_from_storage_before_removing_repository_row() {
        UUID id = UUID.randomUUID();
        GalleryImage existing = savedImage(Item.Category.COSTUME, "https://cdn/full.jpg", "https://cdn/thumb.jpg");
        when(galleryImageRepository.findById(id)).thenReturn(Optional.of(existing));

        galleryService.delete(id);

        var inOrder = inOrder(imageStorageService, galleryImageRepository);
        inOrder.verify(imageStorageService).deleteImage("https://cdn/full.jpg", "https://cdn/thumb.jpg");
        inOrder.verify(galleryImageRepository).delete(existing);
    }

    @Test
    void should_throw_not_found_when_deleting_unknown_id() {
        UUID id = UUID.randomUUID();
        when(galleryImageRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> galleryService.delete(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Gallery image not found");
        verifyNoInteractions(imageStorageService);
    }

    // ─── listPublic / listAdmin ─────────────────────────────────────────────

    @Test
    void should_list_public_active_images_across_all_categories_when_category_is_null() {
        GalleryImage image = savedImage(Item.Category.COSTUME, "https://cdn/full.jpg", "https://cdn/thumb.jpg");
        when(galleryImageRepository.findByIsActiveTrueOrderByCreatedAtDescIdDesc()).thenReturn(List.of(image));

        List<GalleryImageResponse> result = galleryService.listPublic(null);

        assertThat(result).hasSize(1);
        verify(galleryImageRepository).findByIsActiveTrueOrderByCreatedAtDescIdDesc();
        verifyNoMoreInteractions(galleryImageRepository);
    }

    @Test
    void should_list_public_active_images_filtered_by_category() {
        Item.Category category = Item.Category.DRESS;
        GalleryImage image = savedImage(category, "https://cdn/full.jpg", "https://cdn/thumb.jpg");
        when(galleryImageRepository.findByCategoryAndIsActiveTrueOrderByCreatedAtDescIdDesc(category)).thenReturn(List.of(image));

        List<GalleryImageResponse> result = galleryService.listPublic(category);

        assertThat(result).hasSize(1);
        verify(galleryImageRepository).findByCategoryAndIsActiveTrueOrderByCreatedAtDescIdDesc(category);
        verifyNoMoreInteractions(galleryImageRepository);
    }

    @Test
    void should_list_admin_all_images_across_all_categories_when_category_is_null() {
        GalleryImage image = savedImage(Item.Category.COSTUME, "https://cdn/full.jpg", "https://cdn/thumb.jpg");
        when(galleryImageRepository.findAllByOrderByCreatedAtDescIdDesc()).thenReturn(List.of(image));

        List<GalleryImageResponse> result = galleryService.listAdmin(null);

        assertThat(result).hasSize(1);
        verify(galleryImageRepository).findAllByOrderByCreatedAtDescIdDesc();
        verifyNoMoreInteractions(galleryImageRepository);
    }

    @Test
    void should_list_admin_images_filtered_by_category() {
        Item.Category category = Item.Category.DRESS;
        GalleryImage image = savedImage(category, "https://cdn/full.jpg", "https://cdn/thumb.jpg");
        when(galleryImageRepository.findByCategoryOrderByCreatedAtDescIdDesc(category)).thenReturn(List.of(image));

        List<GalleryImageResponse> result = galleryService.listAdmin(category);

        assertThat(result).hasSize(1);
        verify(galleryImageRepository).findByCategoryOrderByCreatedAtDescIdDesc(category);
        verifyNoMoreInteractions(galleryImageRepository);
    }
}
