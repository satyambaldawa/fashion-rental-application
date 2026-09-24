package com.fashionrental.review;

import com.fashionrental.common.exception.ValidationException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewImageUploaderTest {

    @Mock
    private ImageStorageService imageStorageService;

    @InjectMocks
    private ReviewImageUploader uploader;

    private static MockMultipartFile jpeg(String name, int sizeBytes) {
        return new MockMultipartFile(name, name + ".jpg", "image/jpeg", new byte[sizeBytes]);
    }

    @Test
    void should_return_empty_list_when_no_files_are_supplied() {
        assertThat(uploader.uploadAll(null)).isEmpty();
        assertThat(uploader.uploadAll(new MultipartFile[0])).isEmpty();
    }

    @Test
    void should_reject_when_more_than_three_images_are_supplied() {
        MultipartFile[] files = { jpeg("a", 10), jpeg("b", 10), jpeg("c", 10), jpeg("d", 10) };

        assertThatThrownBy(() -> uploader.uploadAll(files))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("at most 3");
    }

    @Test
    void should_reject_when_an_image_exceeds_five_megabytes() {
        MultipartFile[] files = { jpeg("big", 5 * 1024 * 1024 + 1) };

        assertThatThrownBy(() -> uploader.uploadAll(files))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("5MB");
    }

    @Test
    void should_reject_when_a_supplied_photo_is_empty() {
        MultipartFile[] files = { new MockMultipartFile("empty", "empty.jpg", "image/jpeg", new byte[0]) };

        assertThatThrownBy(() -> uploader.uploadAll(files))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void should_reject_when_content_type_is_not_an_allowed_image_type() {
        MultipartFile[] files = {
                new MockMultipartFile("doc", "doc.pdf", "application/pdf", new byte[10])
        };

        assertThatThrownBy(() -> uploader.uploadAll(files))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("JPEG");
    }

    @Test
    void should_validate_every_file_before_uploading_any() throws IOException {
        MultipartFile[] files = { jpeg("ok", 10), jpeg("big", 5 * 1024 * 1024 + 1) };

        assertThatThrownBy(() -> uploader.uploadAll(files)).isInstanceOf(ValidationException.class);

        verify(imageStorageService, never()).uploadImage(anyString(), any(), any(), any(), anyLong());
    }

    @Test
    void should_upload_every_file_under_the_reviews_namespace() throws IOException {
        when(imageStorageService.uploadImage(eq("reviews"), any(), any(), any(), anyLong()))
                .thenReturn(new UploadResult("full-1", "thumb-1"), new UploadResult("full-2", "thumb-2"));

        List<UploadResult> results = uploader.uploadAll(new MultipartFile[] { jpeg("a", 10), jpeg("b", 10) });

        assertThat(results).containsExactly(
                new UploadResult("full-1", "thumb-1"),
                new UploadResult("full-2", "thumb-2"));
    }

    @Test
    void should_delete_every_uploaded_object_when_asked() {
        uploader.deleteAll(List.of(new UploadResult("full-1", "thumb-1"), new UploadResult("full-2", "thumb-2")));

        verify(imageStorageService).deleteImage("full-1", "thumb-1");
        verify(imageStorageService).deleteImage("full-2", "thumb-2");
    }

    @Test
    void should_delete_earlier_uploads_when_a_later_upload_throws_a_runtime_exception() throws IOException {
        UploadResult firstResult = new UploadResult("full-1", "thumb-1");
        when(imageStorageService.uploadImage(eq("reviews"), any(), any(), any(), anyLong()))
                .thenReturn(firstResult)
                .thenThrow(new RuntimeException("R2 unavailable"));

        MultipartFile[] files = { jpeg("a", 10), jpeg("b", 10) };

        assertThatThrownBy(() -> uploader.uploadAll(files)).isInstanceOf(IllegalStateException.class);

        verify(imageStorageService).deleteImage("full-1", "thumb-1");
    }
}
