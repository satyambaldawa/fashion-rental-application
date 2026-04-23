package com.fashionrental.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionrental.common.exception.ResourceNotFoundException;
import com.fashionrental.common.exception.ValidationException;
import com.fashionrental.inventory.model.request.CreateItemRequest;
import com.fashionrental.inventory.model.response.AvailabilityResponse;
import com.fashionrental.inventory.model.response.ItemDetailResponse;
import com.fashionrental.inventory.model.response.ItemSummaryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ItemController.class)
class ItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ItemService itemService;

    @MockitoBean
    private AvailabilityService availabilityService;

    // ── Required by JwtAuthFilter and SecurityConfig wiring in @WebMvcTest ──
    @MockitoBean
    private com.fashionrental.config.JwtConfig jwtConfig;
    @MockitoBean
    private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    private ItemSummaryResponse summaryResponse(UUID id, String name) {
        return new ItemSummaryResponse(
                id, name, Item.Category.COSTUME, Item.ItemType.INDIVIDUAL, "M",
                null,   // description
                200, 1000, 3, 2, true, null, List.of(),
                null    // componentNames
        );
    }

    private ItemDetailResponse detailResponse(UUID id, String name) {
        return new ItemDetailResponse(
                id, name, Item.Category.COSTUME, Item.ItemType.INDIVIDUAL,
                "M", "A fine costume", 200, 1000, 3, true, null,
                null, null, null,
                List.of(), OffsetDateTime.now(), OffsetDateTime.now()
        );
    }

    // ─── GET /api/items ──────────────────────────────────────────────────────

    @Test
    @WithMockUser
    void should_return_200_with_item_list_when_authenticated() throws Exception {
        UUID id = UUID.randomUUID();
        Page<ItemSummaryResponse> page = new PageImpl<>(
                List.of(summaryResponse(id, "Blue Sherwani")),
                PageRequest.of(0, 20), 1
        );
        when(itemService.listItems(null, null, null, 0, 20, null, null)).thenReturn(page);

        mockMvc.perform(get("/api/items").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].name").value("Blue Sherwani"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void should_return_401_when_not_authenticated() throws Exception {
        mockMvc.perform(get("/api/items"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void should_pass_search_and_category_filters_to_service() throws Exception {
        Page<ItemSummaryResponse> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(itemService.listItems(eq("sherwani"), eq(Item.Category.COSTUME), eq(null), eq(0), eq(20), any(), any()))
                .thenReturn(emptyPage);

        mockMvc.perform(get("/api/items")
                        .param("search", "sherwani")
                        .param("category", "COSTUME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)));
    }

    @Test
    @WithMockUser
    void should_return_empty_page_when_no_items_match_filters() throws Exception {
        Page<ItemSummaryResponse> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(itemService.listItems(any(), any(), any(), eq(0), eq(20), any(), any())).thenReturn(emptyPage);

        mockMvc.perform(get("/api/items").param("search", "zzz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    // ─── GET /api/items/{id} ─────────────────────────────────────────────────

    @Test
    @WithMockUser
    void should_return_200_with_item_detail_for_valid_id() throws Exception {
        UUID id = UUID.randomUUID();
        when(itemService.getItem(id)).thenReturn(detailResponse(id, "Golden Sherwani"));

        mockMvc.perform(get("/api/items/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(id.toString()))
                .andExpect(jsonPath("$.data.name").value("Golden Sherwani"))
                .andExpect(jsonPath("$.data.isActive").value(true))
                .andExpect(jsonPath("$.data.photos", hasSize(0)));
    }

    @Test
    @WithMockUser
    void should_return_404_when_item_not_found() throws Exception {
        UUID id = UUID.randomUUID();
        when(itemService.getItem(id)).thenThrow(new ResourceNotFoundException("Item not found: " + id));

        mockMvc.perform(get("/api/items/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value(containsString("Item not found")));
    }

    // ─── POST /api/items ─────────────────────────────────────────────────────

    @Test
    @WithMockUser
    void should_return_201_when_item_created_with_valid_request() throws Exception {
        UUID id = UUID.randomUUID();
        CreateItemRequest request = new CreateItemRequest(
                "Blue Sherwani", Item.Category.COSTUME, Item.ItemType.INDIVIDUAL, "M",
                "Traditional sherwani", 200, 1000, 3, null, null, null, null
        );
        when(itemService.createItem(any(CreateItemRequest.class))).thenReturn(detailResponse(id, "Blue Sherwani"));

        mockMvc.perform(post("/api/items").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Blue Sherwani"));
    }

    @Test
    @WithMockUser
    void should_return_400_when_name_is_blank() throws Exception {
        CreateItemRequest request = new CreateItemRequest(
                "", Item.Category.COSTUME, Item.ItemType.INDIVIDUAL, null, null, 200, 1000, 1, null, null, null, null
        );

        mockMvc.perform(post("/api/items").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser
    void should_return_400_when_rate_is_zero() throws Exception {
        CreateItemRequest request = new CreateItemRequest(
                "Test Item", Item.Category.DRESS, Item.ItemType.INDIVIDUAL, null, null, 0, 0, 1, null, null, null, null
        );

        mockMvc.perform(post("/api/items").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser
    void should_return_400_when_quantity_is_zero() throws Exception {
        CreateItemRequest request = new CreateItemRequest(
                "Test Item", Item.Category.DRESS, Item.ItemType.INDIVIDUAL, null, null, 100, 0, 0, null, null, null, null
        );

        mockMvc.perform(post("/api/items").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser
    void should_return_400_when_category_is_missing() throws Exception {
        // Omit category (null) — @NotNull should trigger
        String body = "{\"name\":\"Test\",\"rate\":100,\"deposit\":0,\"quantity\":1}";

        mockMvc.perform(post("/api/items").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─── GET /api/items/{id}/availability ────────────────────────────────────

    @Test
    @WithMockUser
    void should_return_availability_for_valid_date_range() throws Exception {
        UUID id = UUID.randomUUID();
        OffsetDateTime start = OffsetDateTime.parse("2026-04-20T10:00:00+05:30");
        OffsetDateTime end = OffsetDateTime.parse("2026-04-22T10:00:00+05:30");

        when(availabilityService.getAvailableQuantity(eq(id), any(), any())).thenReturn(2);

        mockMvc.perform(get("/api/items/{id}/availability", id)
                        .param("startDatetime", "2026-04-20T10:00:00+05:30")
                        .param("endDatetime", "2026-04-22T10:00:00+05:30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.itemId").value(id.toString()))
                .andExpect(jsonPath("$.data.availableQuantity").value(2))
                .andExpect(jsonPath("$.data.isAvailable").value(true));
    }

    @Test
    @WithMockUser
    void should_return_is_available_false_when_no_units_free() throws Exception {
        UUID id = UUID.randomUUID();
        when(availabilityService.getAvailableQuantity(eq(id), any(), any())).thenReturn(0);

        mockMvc.perform(get("/api/items/{id}/availability", id)
                        .param("startDatetime", "2026-04-20T10:00:00+05:30")
                        .param("endDatetime", "2026-04-22T10:00:00+05:30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableQuantity").value(0))
                .andExpect(jsonPath("$.data.isAvailable").value(false));
    }

    @Test
    @WithMockUser
    void should_return_400_when_end_datetime_is_not_after_start_datetime() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(get("/api/items/{id}/availability", id)
                        .param("startDatetime", "2026-04-22T10:00:00+05:30")
                        .param("endDatetime", "2026-04-20T10:00:00+05:30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value(containsString("endDatetime must be after startDatetime")));
    }

    @Test
    @WithMockUser
    void should_return_availability_without_date_range_for_general_browse() throws Exception {
        UUID id = UUID.randomUUID();
        when(availabilityService.getAvailableQuantity(eq(id), eq(null), eq(null))).thenReturn(3);

        mockMvc.perform(get("/api/items/{id}/availability", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableQuantity").value(3))
                .andExpect(jsonPath("$.data.isAvailable").value(true));
    }
}
