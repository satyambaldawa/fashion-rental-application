package com.fashionrental.gallery;

import com.fashionrental.config.SecurityConfig;
import com.fashionrental.config.SecurityErrorHandler;
import com.fashionrental.gallery.model.response.GalleryImageResponse;
import com.fashionrental.inventory.Item;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GalleryPublicController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class})
class GalleryPublicControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GalleryService galleryService;

    // ── Required by JwtAuthFilter and SecurityConfig wiring in @WebMvcTest ──
    @MockitoBean
    private com.fashionrental.config.JwtConfig jwtConfig;

    private GalleryImageResponse response(Item.Category category) {
        return new GalleryImageResponse(UUID.randomUUID(), category, "https://cdn/full.jpg", "https://cdn/thumb.jpg", null, true);
    }

    @Test
    void should_return_active_gallery_images() throws Exception {
        when(galleryService.listPublic(isNull())).thenReturn(List.of(response(Item.Category.COSTUME)));

        mockMvc.perform(get("/api/public/gallery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void should_pass_category_filter_through_to_service() throws Exception {
        when(galleryService.listPublic(eq(Item.Category.DRESS))).thenReturn(List.of(response(Item.Category.DRESS)));

        mockMvc.perform(get("/api/public/gallery").param("category", "DRESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        verify(galleryService).listPublic(Item.Category.DRESS);
    }

    @Test
    void should_return_400_when_category_is_invalid() throws Exception {
        mockMvc.perform(get("/api/public/gallery").param("category", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
