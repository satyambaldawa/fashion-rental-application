package com.fashionrental.gallery;

import com.fashionrental.common.response.ApiResponse;
import com.fashionrental.gallery.model.request.UpdateGalleryImageRequest;
import com.fashionrental.gallery.model.response.GalleryImageResponse;
import com.fashionrental.inventory.Item;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "Gallery", description = "Admin gallery image management")
@RestController
@RequestMapping("/api/gallery")
public class GalleryAdminController {

    private final GalleryService galleryService;

    public GalleryAdminController(GalleryService galleryService) {
        this.galleryService = galleryService;
    }

    @Operation(summary = "List gallery images, optionally filtered by category")
    @GetMapping
    public ResponseEntity<ApiResponse<List<GalleryImageResponse>>> listGalleryImages(
            @RequestParam(required = false) Item.Category category
    ) {
        return ResponseEntity.ok(ApiResponse.ok(galleryService.listAdmin(category)));
    }

    @Operation(summary = "Upload one or more gallery images for a category")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    schemaProperties = {
                            @SchemaProperty(
                                    name = "files",
                                    array = @ArraySchema(schema = @Schema(type = "string", format = "binary"))
                            ),
                            @SchemaProperty(
                                    name = "category",
                                    schema = @Schema(implementation = Item.Category.class)
                            )
                    }
            )
    )
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<List<GalleryImageResponse>>> uploadGalleryImages(
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestParam("files") MultipartFile[] files,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @RequestParam("category") Item.Category category
    ) {
        List<GalleryImageResponse> uploaded = galleryService.upload(category, files);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(uploaded));
    }

    @Operation(summary = "Update a gallery image's caption, sort order, or active state")
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<GalleryImageResponse>> updateGalleryImage(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateGalleryImageRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(galleryService.update(id, request)));
    }

    @Operation(summary = "Delete a gallery image")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGalleryImage(@PathVariable UUID id) {
        galleryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
