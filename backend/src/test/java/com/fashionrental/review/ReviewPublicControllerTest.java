package com.fashionrental.review;

import com.fashionrental.common.exception.RateLimitExceededException;
import com.fashionrental.config.JwtConfig;
import com.fashionrental.config.SecurityConfig;
import com.fashionrental.config.SecurityErrorHandler;
import com.fashionrental.review.model.ReviewSort;
import com.fashionrental.review.model.response.SubmitReviewResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewPublicController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class})
class ReviewPublicControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ReviewService reviewService;

    // ── Required by JwtAuthFilter and SecurityConfig wiring in @WebMvcTest ──
    @MockitoBean private JwtConfig jwtConfig;

    private static MockMultipartFile reviewPart(String json) {
        return new MockMultipartFile("review", "review", MediaType.APPLICATION_JSON_VALUE, json.getBytes());
    }

    private static final String VALID_JSON = """
            {"reviewerName":"Priya S","phone":"9876543210",
             "itemDescription":"Red lehenga","rating":5,"reviewText":"Lovely."}
            """;

    @Test
    void should_return_201_with_pending_status_on_successful_submission() throws Exception {
        when(reviewService.submit(any(), any(), any()))
                .thenReturn(new SubmitReviewResponse(UUID.randomUUID(), Review.Status.PENDING));

        mockMvc.perform(multipart("/api/public/reviews").file(reviewPart(VALID_JSON)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void should_return_429_when_the_rate_limit_is_exceeded() throws Exception {
        when(reviewService.submit(any(), any(), any()))
                .thenThrow(new RateLimitExceededException("Too many reviews submitted. Please try again in an hour."));

        mockMvc.perform(multipart("/api/public/reviews").file(reviewPart(VALID_JSON)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Too many reviews submitted. Please try again in an hour."));
    }

    @Test
    void should_return_400_when_the_review_payload_fails_validation() throws Exception {
        String badJson = """
                {"reviewerName":"","phone":"123","itemDescription":"x","rating":9,"reviewText":""}
                """;

        mockMvc.perform(multipart("/api/public/reviews").file(reviewPart(badJson)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void should_return_400_when_the_review_part_is_missing() throws Exception {
        mockMvc.perform(multipart("/api/public/reviews"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void should_default_sort_to_newest_when_the_sort_parameter_is_absent() throws Exception {
        when(reviewService.listPublic(any(), anyInt())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/public/reviews")).andExpect(status().isOk());

        verify(reviewService).listPublic(ReviewSort.NEWEST, 0);
    }

    @Test
    void should_pass_the_requested_sort_and_page_through() throws Exception {
        when(reviewService.listPublic(any(), anyInt())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/public/reviews").param("sort", "HIGHEST_RATED").param("page", "2"))
                .andExpect(status().isOk());

        verify(reviewService).listPublic(ReviewSort.HIGHEST_RATED, 2);
    }

    @Test
    void should_allow_listing_without_authentication() throws Exception {
        when(reviewService.listPublic(any(), anyInt())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/public/reviews")).andExpect(status().isOk());
    }
}
