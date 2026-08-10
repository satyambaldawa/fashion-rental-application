package com.fashionrental.gallery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionrental.common.exception.ResourceNotFoundException;
import com.fashionrental.config.SecurityConfig;
import com.fashionrental.config.SecurityErrorHandler;
import com.fashionrental.gallery.model.request.UpdateGalleryImageRequest;
import com.fashionrental.gallery.model.response.GalleryImageResponse;
import com.fashionrental.inventory.Item;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GalleryAdminController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class})
class GalleryAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GalleryService galleryService;

    // ── Required by JwtAuthFilter and SecurityConfig wiring in @WebMvcTest ──
    @MockitoBean
    private com.fashionrental.config.JwtConfig jwtConfig;

    private GalleryImageResponse response(UUID id, Item.Category category) {
        return new GalleryImageResponse(id, category, "https://cdn/full.jpg", "https://cdn/thumb.jpg", null, true);
    }

    // ─── GET /api/gallery ────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "OWNER")
    void should_return_all_gallery_images_including_inactive() throws Exception {
        when(galleryService.listAdmin(null)).thenReturn(List.of(response(UUID.randomUUID(), Item.Category.COSTUME)));

        mockMvc.perform(get("/api/gallery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    // ─── POST /api/gallery ───────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "OWNER")
    void should_return_201_with_uploaded_images_for_multi_file_upload() throws Exception {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(galleryService.upload(eq(Item.Category.COSTUME), any()))
                .thenReturn(List.of(response(id1, Item.Category.COSTUME), response(id2, Item.Category.COSTUME)));

        MockMultipartFile file1 = new MockMultipartFile("files", "a.jpg", "image/jpeg", new byte[1024]);
        MockMultipartFile file2 = new MockMultipartFile("files", "b.jpg", "image/jpeg", new byte[1024]);

        mockMvc.perform(multipart("/api/gallery")
                        .file(file1)
                        .file(file2)
                        .param("category", "COSTUME")
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].imageUrl").value("https://cdn/full.jpg"))
                .andExpect(jsonPath("$.data[0].thumbnailUrl").value("https://cdn/thumb.jpg"));
    }

    // ─── PATCH /api/gallery/{id} ─────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "OWNER")
    void should_return_200_when_gallery_image_updated() throws Exception {
        UUID id = UUID.randomUUID();
        UpdateGalleryImageRequest request = new UpdateGalleryImageRequest("New caption", true);
        when(galleryService.update(eq(id), any(UpdateGalleryImageRequest.class))).thenReturn(response(id, Item.Category.COSTUME));

        mockMvc.perform(patch("/api/gallery/{id}", id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void should_return_404_when_updating_unknown_gallery_image() throws Exception {
        UUID id = UUID.randomUUID();
        UpdateGalleryImageRequest request = new UpdateGalleryImageRequest(null, null);
        when(galleryService.update(eq(id), any(UpdateGalleryImageRequest.class)))
                .thenThrow(new ResourceNotFoundException("Gallery image not found: " + id));

        mockMvc.perform(patch("/api/gallery/{id}", id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─── DELETE /api/gallery/{id} ────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "OWNER")
    void should_return_204_when_gallery_image_deleted() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(galleryService).delete(id);

        mockMvc.perform(delete("/api/gallery/{id}", id).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void should_return_404_when_deleting_unknown_gallery_image() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("Gallery image not found: " + id)).when(galleryService).delete(id);

        mockMvc.perform(delete("/api/gallery/{id}", id).with(csrf()))
                .andExpect(status().isNotFound());
    }

    // ─── SECURITY: OWNER-only ────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "EXECUTIVE")
    void should_return_403_for_executive_role() throws Exception {
        mockMvc.perform(get("/api/gallery"))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_return_401_when_not_authenticated() throws Exception {
        mockMvc.perform(get("/api/gallery"))
                .andExpect(status().isUnauthorized());
    }
}
