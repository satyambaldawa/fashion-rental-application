package com.fashionrental.inventory;

import com.fashionrental.config.JwtConfig;
import com.fashionrental.config.SecurityConfig;
import com.fashionrental.config.SecurityErrorHandler;
import com.fashionrental.inventory.model.request.PhotoOrderItem;
import com.fashionrental.inventory.model.request.ReorderPhotosRequest;
import com.fashionrental.inventory.model.response.ItemPhotoResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ItemPhotoController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class})
class ItemPhotoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ItemPhotoService itemPhotoService;

    @MockitoBean
    private ItemPhotoRepository itemPhotoRepository;

    @MockitoBean
    private JwtConfig jwtConfig;

    private ItemPhoto photo(UUID id, int sortOrder) {
        ItemPhoto photo = new ItemPhoto();
        photo.setUrl("https://cdn.example.com/" + id + ".jpg");
        photo.setThumbnailUrl("https://cdn.example.com/" + id + "-thumb.jpg");
        photo.setSortOrder(sortOrder);
        try {
            var field = ItemPhoto.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(photo, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return photo;
    }

    // ─── GET /api/items/{itemId}/photos ──────────────────────────────────────

    @Test
    @WithMockUser
    void should_list_photos_ordered_by_sort_order() throws Exception {
        UUID itemId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        when(itemPhotoRepository.findByItemIdOrderBySortOrderAsc(itemId)).thenReturn(List.of(photo(photoId, 0)));

        mockMvc.perform(get("/api/items/{itemId}/photos", itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].sortOrder").value(0));
    }

    @Test
    void should_return_401_when_listing_photos_without_authentication() throws Exception {
        mockMvc.perform(get("/api/items/{itemId}/photos", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ─── POST /api/items/{itemId}/photos ─────────────────────────────────────

    @Test
    @WithMockUser(roles = "OWNER")
    void should_upload_photo_and_return_201() throws Exception {
        UUID itemId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "sherwani.jpg", "image/jpeg", "fake-bytes".getBytes());
        ItemPhotoResponse response = new ItemPhotoResponse(photoId, "https://cdn.example.com/x.jpg", "https://cdn.example.com/x-thumb.jpg", 0);
        when(itemPhotoService.uploadPhoto(eq(itemId), any())).thenReturn(response);

        mockMvc.perform(multipart("/api/items/{itemId}/photos", itemId).file(file).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(photoId.toString()));
    }

    @Test
    void should_return_401_when_uploading_photo_without_authentication() throws Exception {
        UUID itemId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "sherwani.jpg", "image/jpeg", "fake-bytes".getBytes());

        mockMvc.perform(multipart("/api/items/{itemId}/photos", itemId).file(file).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "EXECUTIVE")
    void should_return_403_when_executive_uploads_photo() throws Exception {
        UUID itemId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "sherwani.jpg", "image/jpeg", "fake-bytes".getBytes());

        mockMvc.perform(multipart("/api/items/{itemId}/photos", itemId).file(file).with(csrf()))
                .andExpect(status().isForbidden());

        verify(itemPhotoService, never()).uploadPhoto(any(), any());
    }

    // ─── DELETE /api/items/{itemId}/photos/{photoId} ─────────────────────────

    @Test
    @WithMockUser(roles = "OWNER")
    void should_delete_photo_and_return_204() throws Exception {
        UUID itemId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        doNothing().when(itemPhotoService).deletePhoto(itemId, photoId);

        mockMvc.perform(delete("/api/items/{itemId}/photos/{photoId}", itemId, photoId).with(csrf()))
                .andExpect(status().isNoContent());
    }

    // ─── PATCH /api/items/{itemId}/photos/order ──────────────────────────────

    @Test
    @WithMockUser(roles = "OWNER")
    void should_reorder_photos_when_request_is_valid() throws Exception {
        UUID itemId = UUID.randomUUID();
        ReorderPhotosRequest request = new ReorderPhotosRequest(List.of(new PhotoOrderItem(UUID.randomUUID(), 1)));
        doNothing().when(itemPhotoService).reorderPhotos(eq(itemId), any());

        mockMvc.perform(patch("/api/items/{itemId}/photos/order", itemId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void should_return_400_when_reorder_list_is_empty() throws Exception {
        UUID itemId = UUID.randomUUID();
        ReorderPhotosRequest request = new ReorderPhotosRequest(List.of());

        mockMvc.perform(patch("/api/items/{itemId}/photos/order", itemId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
