package com.fashionrental.inventory;

import com.fashionrental.common.exception.ValidationException;
import com.fashionrental.common.response.ApiResponse;
import com.fashionrental.inventory.model.request.CreateItemRequest;
import com.fashionrental.inventory.model.response.AvailabilityResponse;
import com.fashionrental.inventory.model.response.ItemDetailResponse;
import com.fashionrental.inventory.model.response.ItemSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Tag(name = "Inventory", description = "Inventory item management")
@RestController
@RequestMapping("/api/items")
public class ItemController {

    private final ItemService itemService;
    private final AvailabilityService availabilityService;

    public ItemController(ItemService itemService, AvailabilityService availabilityService) {
        this.itemService = itemService;
        this.availabilityService = availabilityService;
    }

    @Operation(summary = "List all active items with optional filters")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ItemSummaryResponse>>> listItems(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Item.Category category,
            @RequestParam(name = "itemSize", required = false) String itemSize,
            @RequestParam(required = false) OffsetDateTime startDatetime,
            @RequestParam(required = false) OffsetDateTime endDatetime
    ) {
        Page<ItemSummaryResponse> result = itemService.listItems(search, category, itemSize, page, size, startDatetime, endDatetime);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @Operation(summary = "Get item details by ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ItemDetailResponse>> getItem(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(itemService.getItem(id)));
    }

    @Operation(summary = "Create a new inventory item")
    @PostMapping
    public ResponseEntity<ApiResponse<ItemDetailResponse>> createItem(
            @Valid @RequestBody CreateItemRequest request
    ) {
        ItemDetailResponse created = itemService.createItem(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @Operation(summary = "Check item availability for a date range")
    @GetMapping("/{id}/availability")
    public ResponseEntity<ApiResponse<AvailabilityResponse>> checkAvailability(
            @PathVariable UUID id,
            @RequestParam(required = false) OffsetDateTime startDatetime,
            @RequestParam(required = false) OffsetDateTime endDatetime
    ) {
        if (startDatetime != null && endDatetime != null && !endDatetime.isAfter(startDatetime)) {
            throw new ValidationException("endDatetime must be after startDatetime");
        }

        int available = availabilityService.getAvailableQuantity(id, startDatetime, endDatetime);
        AvailabilityResponse response = new AvailabilityResponse(id, available, available > 0);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
