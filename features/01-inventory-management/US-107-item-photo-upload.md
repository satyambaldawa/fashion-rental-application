# US-107: Item Photo Upload and Management

**Epic:** Inventory Management
**Priority:** P0
**Depends On:** US-104 (item must exist), SETUP-01 (Cloudflare R2 configured)
**Blocks:** Nothing (but improves item browsing UX)

---

## User Story

> As a Shop Owner, I want to upload photos of items and manage them (reorder, delete), so that staff can visually identify items when browsing inventory.

---

## Acceptance Criteria

- [ ] Up to 8 photos per item
- [ ] Accepted formats: JPEG, PNG, WebP (any phone camera output)
- [ ] Max upload size: 15 MB per photo
- [ ] System resizes and converts to WebP on upload: full (1200px max) + thumbnail (300px)
- [ ] Both URLs stored in `item_photos` table; served directly from R2 CDN
- [ ] Photos can be reordered (drag-and-drop or sort order input); first photo is the cover/thumbnail shown in list view
- [ ] Photos can be deleted (removed from R2 and database)
- [ ] Uploading while already at 8 photos returns an error

---

## Backend Implementation

### Entity: `ItemPhoto.java`

```java
@Entity
@Table(name = "item_photos")
public class ItemPhoto {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(nullable = false)
    private String url;                 // full-size WebP on R2

    @Column(name = "thumbnail_url", nullable = false)
    private String thumbnailUrl;        // 300px WebP on R2

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
```

### ImageStorageService Interface

```java
public interface ImageStorageService {
    UploadResult uploadImage(UUID itemId, InputStream inputStream, String originalFilename) throws IOException;
    void deleteImage(String fullUrl, String thumbnailUrl);
}

public record UploadResult(String fullUrl, String thumbnailUrl) {}
```

### CloudflareR2ImageStorageService Implementation

```java
@Service
public class CloudflareR2ImageStorageService implements ImageStorageService {

    private final S3Client s3Client;
    private final String bucketName;
    private final String publicUrlBase;
    private final int fullMaxPx;
    private final int thumbMaxPx;

    @Override
    public UploadResult uploadImage(UUID itemId, InputStream inputStream, String originalFilename) throws IOException {
        String uuid = UUID.randomUUID().toString();
        String fullKey  = "items/" + itemId + "/" + uuid + "-full.webp";
        String thumbKey = "items/" + itemId + "/" + uuid + "-thumb.webp";

        // Process full-size image
        byte[] fullBytes  = processImage(inputStream, fullMaxPx);
        byte[] thumbBytes = processImage(new ByteArrayInputStream(fullBytes), thumbMaxPx);

        // Upload both to R2
        uploadToR2(fullKey, fullBytes);
        uploadToR2(thumbKey, thumbBytes);

        return new UploadResult(
            publicUrlBase + "/" + fullKey,
            publicUrlBase + "/" + thumbKey
        );
    }

    private byte[] processImage(InputStream input, int maxPx) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thumbnails.of(input)
            .size(maxPx, maxPx)
            .keepAspectRatio(true)
            .outputFormat("webp")
            .toOutputStream(out);
        return out.toByteArray();
    }

    private void uploadToR2(String key, byte[] data) {
        s3Client.putObject(
            PutObjectRequest.builder().bucket(bucketName).key(key).contentType("image/webp").build(),
            RequestBody.fromBytes(data)
        );
    }

    @Override
    public void deleteImage(String fullUrl, String thumbnailUrl) {
        // Extract key from URL and delete from R2
        String fullKey  = fullUrl.replace(publicUrlBase + "/", "");
        String thumbKey = thumbnailUrl.replace(publicUrlBase + "/", "");
        s3Client.deleteObject(b -> b.bucket(bucketName).key(fullKey));
        s3Client.deleteObject(b -> b.bucket(bucketName).key(thumbKey));
    }
}
```

### ItemPhotoService

```java
@Service
@Transactional
public class ItemPhotoService {

    public ItemPhotoResponse uploadPhoto(UUID itemId, MultipartFile file) throws IOException {
        Item item = itemRepository.findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("Item not found"));

        long photoCount = itemPhotoRepository.countByItemId(itemId);
        if (photoCount >= 8) {
            throw new ConflictException("Maximum 8 photos per item reached");
        }
        if (file.getSize() > 15 * 1024 * 1024) {
            throw new ValidationException("File size exceeds 15 MB limit");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ValidationException("Only image files are accepted");
        }

        UploadResult result = imageStorageService.uploadImage(itemId, file.getInputStream(), file.getOriginalFilename());

        int nextOrder = (int) photoCount;
        ItemPhoto photo = new ItemPhoto();
        photo.setItem(item);
        photo.setUrl(result.fullUrl());
        photo.setThumbnailUrl(result.thumbnailUrl());
        photo.setSortOrder(nextOrder);
        photo.setCreatedAt(OffsetDateTime.now());

        return toResponse(itemPhotoRepository.save(photo));
    }

    public void deletePhoto(UUID itemId, UUID photoId) {
        ItemPhoto photo = itemPhotoRepository.findByIdAndItemId(photoId, itemId)
            .orElseThrow(() -> new ResourceNotFoundException("Photo not found"));
        imageStorageService.deleteImage(photo.getUrl(), photo.getThumbnailUrl());
        itemPhotoRepository.delete(photo);
    }

    public void reorderPhotos(UUID itemId, List<PhotoOrderRequest> orders) {
        orders.forEach(o -> itemPhotoRepository.updateSortOrder(o.id(), o.sortOrder()));
    }
}
```

### API Endpoints

```
GET    /api/items/{id}/photos
       Response: [{ id, url, thumbnailUrl, sortOrder }]

POST   /api/items/{id}/photos
       Content-Type: multipart/form-data
       Field: file (image, max 15MB)
       Response 201: { id, url, thumbnailUrl, sortOrder }
       Response 409: max photos reached
       Response 400: invalid file type or too large

DELETE /api/items/{id}/photos/{photoId}
       Response 204

PATCH  /api/items/{id}/photos/order
       Body: [{ "id": "uuid", "sortOrder": 0 }, { "id": "uuid", "sortOrder": 1 }]
       Response 200
```

---

## Frontend Implementation

### Photo Manager Component

```tsx
// Shown on item detail page
// - Upload zone (drag-and-drop or click to browse)
// - Thumbnail grid with delete button on each
// - Drag to reorder (Ant Design DragSortTable or react-beautiful-dnd)

<Upload
  accept="image/*"
  maxCount={8}
  listType="picture-card"
  customRequest={({ file }) => uploadPhoto(itemId, file)}
  onRemove={(file) => deletePhoto(itemId, file.uid)}
>
  {photoCount < 8 && <div>+ Upload</div>}
</Upload>
```

---

## Test Cases

| Scenario | Expected |
|----------|----------|
| Upload 5MB JPEG | Processed to WebP, resized, two URLs stored, 201 response |
| Upload 20MB file | 400: "File size exceeds 15 MB limit" |
| Upload non-image (PDF) | 400: "Only image files are accepted" |
| Upload when already 8 photos | 409: "Maximum 8 photos per item reached" |
| Delete photo | Removed from R2 and database, no longer in list |
| Reorder photos | sort_order updated, first photo becomes new cover in list view |
