package com.fashionrental.inventory;

import com.fashionrental.common.response.ApiResponse;
import com.fashionrental.inventory.model.request.ReorderPhotosRequest;
import com.fashionrental.inventory.model.response.ItemPhotoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "Inventory", description = "Item photo management")
@RestController
@RequestMapping("/api/items/{itemId}/photos")
public class ItemPhotoController {

    private final ItemPhotoService itemPhotoService;
    private final ItemPhotoRepository itemPhotoRepository;

    public ItemPhotoController(ItemPhotoService itemPhotoService, ItemPhotoRepository itemPhotoRepository) {
        this.itemPhotoService = itemPhotoService;
        this.itemPhotoRepository = itemPhotoRepository;
    }

    @Operation(summary = "List all photos for an item")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ItemPhotoResponse>>> listPhotos(@PathVariable UUID itemId) {
        List<ItemPhotoResponse> photos = itemPhotoRepository.findByItemIdOrderBySortOrderAsc(itemId).stream()
                .map(p -> new ItemPhotoResponse(p.getId(), p.getUrl(), p.getThumbnailUrl(), p.getSortOrder()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(photos));
    }

    @Operation(summary = "Upload a photo for an item")
    @PostMapping
    public ResponseEntity<ApiResponse<ItemPhotoResponse>> uploadPhoto(
            @PathVariable UUID itemId,
            @RequestParam("file") MultipartFile file
    ) {
        ItemPhotoResponse photo = itemPhotoService.uploadPhoto(itemId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(photo));
    }

    @Operation(summary = "Delete a photo from an item")
    @DeleteMapping("/{photoId}")
    public ResponseEntity<Void> deletePhoto(
            @PathVariable UUID itemId,
            @PathVariable UUID photoId
    ) {
        itemPhotoService.deletePhoto(itemId, photoId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reorder photos for an item")
    @PatchMapping("/order")
    public ResponseEntity<ApiResponse<Void>> reorderPhotos(
            @PathVariable UUID itemId,
            @Valid @RequestBody ReorderPhotosRequest request
    ) {
        itemPhotoService.reorderPhotos(itemId, request);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
