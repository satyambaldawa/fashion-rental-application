package com.fashionrental.gallery;

import com.fashionrental.common.response.ApiResponse;
import com.fashionrental.gallery.model.response.GalleryImageResponse;
import com.fashionrental.inventory.Item;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Gallery", description = "Public gallery image browsing")
@RestController
@RequestMapping("/api/public/gallery")
public class GalleryPublicController {

    private final GalleryService galleryService;

    public GalleryPublicController(GalleryService galleryService) {
        this.galleryService = galleryService;
    }

    @Operation(summary = "List active gallery images, optionally filtered by category")
    @GetMapping
    public ResponseEntity<ApiResponse<List<GalleryImageResponse>>> listGalleryImages(
            @RequestParam(required = false) Item.Category category
    ) {
        return ResponseEntity.ok(ApiResponse.ok(galleryService.listPublic(category)));
    }
}
