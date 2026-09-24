package com.fashionrental.review;

import com.fashionrental.config.JwtConfig;
import com.fashionrental.config.SecurityConfig;
import com.fashionrental.config.SecurityErrorHandler;
import com.fashionrental.review.model.response.AdminReviewResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewAdminController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class})
class ReviewAdminControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ReviewService reviewService;
    @MockitoBean private JwtConfig jwtConfig;

    @Test
    @WithMockUser(roles = "OWNER")
    void should_return_the_moderation_queue_for_an_owner() throws Exception {
        when(reviewService.listForModeration(any(), anyInt(), anyInt())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/reviews")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void should_return_400_when_page_parameter_is_negative() throws Exception {
        when(reviewService.listForModeration(any(), eq(-1), anyInt()))
                .thenThrow(new IllegalArgumentException("Page index must not be less than zero"));

        mockMvc.perform(get("/api/reviews").param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "EXECUTIVE")
    void should_return_403_for_executive_role_on_the_moderation_queue() throws Exception {
        mockMvc.perform(get("/api/reviews")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void should_update_status_for_an_owner() throws Exception {
        UUID id = UUID.randomUUID();
        AdminReviewResponse response = new AdminReviewResponse(
                id, "Priya S", "9876543210", "Red lehenga", 5, "Lovely outfit.",
                Review.Status.APPROVED, OffsetDateTime.now(), OffsetDateTime.now(), List.of());
        when(reviewService.updateStatus(id, Review.Status.APPROVED)).thenReturn(response);

        mockMvc.perform(patch("/api/reviews/" + id + "/status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        verify(reviewService).updateStatus(id, Review.Status.APPROVED);
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void should_delete_a_review_for_an_owner() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/reviews/" + id).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(reviewService).delete(id);
    }

    @Test
    @WithMockUser(roles = "EXECUTIVE")
    void should_return_403_for_executive_role_on_a_status_update() throws Exception {
        mockMvc.perform(patch("/api/reviews/" + UUID.randomUUID() + "/status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EXECUTIVE")
    void should_return_403_for_executive_role_on_delete() throws Exception {
        mockMvc.perform(delete("/api/reviews/" + UUID.randomUUID()).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_return_401_when_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/reviews")).andExpect(status().isUnauthorized());
    }
}
