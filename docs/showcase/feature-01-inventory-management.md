# Inventory Management — Feature Showcase

Stories covered: US-101, US-102, US-103, US-104, US-107

---

## Feature Summary

| Story | Title | Status |
|-------|-------|--------|
| US-101 | Browse inventory with availability | Complete |
| US-102 | Search and filter items | Complete |
| US-103 | Check item availability for a date range | Complete |
| US-104 | Add new item to catalog | Complete |
| US-107 | Item photo upload and management | Complete |
| US-105 | Edit item details | Deferred (P1) |
| US-106 | Adjust unit count | Deferred (P1) |

---

## Architecture Decisions

### Monetary values — whole rupees, no paise

All `rate` and `deposit` fields are stored as `INTEGER` in PostgreSQL (whole rupees). The domain has no paise conversion anywhere. The feature stories originally used `ratePaise`/`depositPaise` naming, but the implementation uses `rate`/`deposit` to reinforce that the value is already in rupees. Frontend sends and displays values in rupees directly.

### JPA Criteria / JpaSpecificationExecutor for search

`ItemRepository` extends `JpaSpecificationExecutor<Item>`. Search and filter logic in `ItemService.listItems()` builds a `Specification<Item>` dynamically, combining `isActive = true`, optional name LIKE, category equality, and size LIKE predicates. This avoids multiple repository query method overloads and handles the null-safe "no filter applied" path cleanly.

### Native SQL for availability queries

Availability counting (`countCurrentlyBookedUnits`, `countBookedUnits`) uses native SQL rather than JPQL. The `receipt_line_items` and `receipts` tables don't have JPA entities yet (they belong to the checkout epic). Native SQL allows the availability query to run without those entities being mapped.

### Two-mode availability

`AvailabilityService.getAvailableQuantity()` handles two cases:
- **No date range**: counts all currently GIVEN units (used in the browse/list view for a general signal).
- **With date range**: applies the overlap condition `start < endDatetime AND end > startDatetime` (used during checkout to confirm actual availability).

### Image storage via interface

`ImageStorageService` is an interface with two implementations: `LocalImageStorageService` (active on the `dev` profile, stores files to `./uploads/`) and `CloudflareR2ImageStorageService` (active on `prod`). The `ItemPhotoService` depends only on the interface, keeping photo upload logic decoupled from storage backend.

### Soft delete via `is_active`

Items are never physically deleted. `getItem()` and `listItems()` both enforce `isActive = true`. An inactive item returns 404 from `getItem()` and is excluded from list queries. `AvailabilityService` short-circuits to 0 for inactive items regardless of bookings.

---

## API Surface

All endpoints require `Authorization: Bearer <token>` except where noted. Every response wraps the payload in `ApiResponse<T>`.

### Items

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/api/items` | List active items with optional filters | Required |
| GET | `/api/items/{id}` | Get item detail by ID | Required |
| POST | `/api/items` | Create a new item | Required |
| GET | `/api/items/{id}/availability` | Check available units (optionally for a date range) | Required |

**GET /api/items — query params**

| Param | Type | Default | Notes |
|-------|------|---------|-------|
| page | int | 0 | Zero-based page number |
| size | int | 20 | Page size |
| search | String | — | Name LIKE (case-insensitive) |
| category | Enum | — | COSTUME, ACCESSORIES, PAGDI, DRESS, ORNAMENTS |
| itemSize | String | — | Size LIKE (case-insensitive) |

**POST /api/items — request body**

```json
{
  "name": "Blue Sherwani",
  "category": "COSTUME",
  "size": "M",
  "description": "Traditional blue sherwani",
  "rate": 200,
  "deposit": 1000,
  "quantity": 3,
  "notes": "Handle with care"
}
```

Required: `name` (non-blank), `category` (non-null), `rate` (≥ 1), `deposit` (≥ 0), `quantity` (≥ 1).

**GET /api/items/{id}/availability — query params**

| Param | Type | Required | Notes |
|-------|------|----------|-------|
| startDatetime | ISO 8601 OffsetDateTime | No | If omitted, returns general availability |
| endDatetime | ISO 8601 OffsetDateTime | No | Must be after startDatetime if provided |

### Photos

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/api/items/{itemId}/photos` | List photos for an item | Required |
| POST | `/api/items/{itemId}/photos` | Upload a photo (multipart/form-data, field: `file`) | Required |
| DELETE | `/api/items/{itemId}/photos/{photoId}` | Delete a photo | Required |
| PATCH | `/api/items/{itemId}/photos/order` | Reorder photos | Required |

**POST photo — constraints**
- Max file size: 15 MB (configurable via `app.storage.image.max-upload-bytes`)
- Accepted content types: `image/jpeg`, `image/png`, `image/webp`, `image/gif`
- Max 8 photos per item (configurable via `app.storage.image.max-photos-per-item`)
- Returns 409 when at max; 400 for invalid type or oversized file

**PATCH /api/items/{itemId}/photos/order — request body**

```json
{ "photos": [{ "id": "uuid", "sortOrder": 0 }, { "id": "uuid", "sortOrder": 1 }] }
```

---

## Test Coverage Summary

**Total: 61 tests, all passing.**

### AvailabilityServiceTest (11 tests)

| Test | Covers |
|------|--------|
| `should_return_zero_when_all_units_booked_for_date_range` | All units booked for an overlapping range |
| `should_return_partial_availability_when_one_of_three_units_booked` | Partial booking |
| `should_return_full_quantity_when_no_overlap_at_exact_boundary` | Exact boundary — receipt ending at query start does NOT overlap |
| `should_return_full_quantity_when_no_bookings_exist_for_date_range` | No bookings in range |
| `should_return_zero_for_inactive_item` | Inactive item with no date range |
| `should_return_zero_for_inactive_item_even_with_date_range` | Inactive item short-circuits even with dates |
| `should_use_current_booking_count_when_no_date_range_given` | Browse mode (no dates) |
| `should_return_zero_currently_booked_units_when_no_active_receipts` | No receipts |
| `should_never_return_negative_availability_when_booked_exceeds_quantity` | Data inconsistency guard (`Math.max(0, ...)`) |
| `should_return_availability_for_all_items_in_batch` | `getAvailableQuantities()` batch method |
| `should_throw_not_found_when_item_does_not_exist` | Missing item |

### ItemServiceTest (12 tests)

| Test | Covers |
|------|--------|
| `should_return_empty_page_when_no_items` | Empty catalog |
| `should_map_item_fields_correctly_in_list_response` | All summary fields mapped correctly |
| `should_set_is_available_false_when_all_units_booked` | `isAvailable = false` when 0 available |
| `should_return_null_thumbnail_when_item_has_no_photos` | No photos → null thumbnail |
| `should_return_first_photo_thumbnail_when_item_has_photos` | First photo is used for thumbnail |
| `should_create_item_with_correct_fields` | Happy-path create |
| `should_create_item_with_is_active_true_by_default` | New items are active |
| `should_create_item_with_zero_deposit_allowed` | Zero deposit is valid |
| `should_throw_not_found_when_item_does_not_exist` | getItem — item missing |
| `should_throw_not_found_when_item_is_inactive` | getItem — inactive item treated as missing |
| `should_return_item_detail_with_all_fields_when_active` | All detail fields present |
| `should_include_photos_in_item_detail_response` | Photos appear in detail response |

### ItemControllerTest (15 tests — @WebMvcTest, MockMvc)

| Test | Covers |
|------|--------|
| `should_return_200_with_item_list_when_authenticated` | GET /api/items happy path |
| `should_return_401_when_not_authenticated` | Unauthenticated request |
| `should_pass_search_and_category_filters_to_service` | Filters forwarded to service |
| `should_return_empty_page_when_no_items_match_filters` | No-match response shape |
| `should_return_200_with_item_detail_for_valid_id` | GET /api/items/{id} happy path |
| `should_return_404_when_item_not_found` | GET /api/items/{id} — not found → 404 |
| `should_return_201_when_item_created_with_valid_request` | POST /api/items happy path |
| `should_return_400_when_name_is_blank` | @NotBlank on name |
| `should_return_400_when_rate_is_zero` | @Min(1) on rate |
| `should_return_400_when_quantity_is_zero` | @Min(1) on quantity |
| `should_return_400_when_category_is_missing` | @NotNull on category |
| `should_return_availability_for_valid_date_range` | GET /{id}/availability with dates |
| `should_return_is_available_false_when_no_units_free` | isAvailable=false in response |
| `should_return_400_when_end_datetime_is_not_after_start_datetime` | Invalid date range |
| `should_return_availability_without_date_range_for_general_browse` | No-date browse availability |

### ItemPhotoServiceTest (10 tests)

| Test | Covers |
|------|--------|
| `should_upload_photo_and_return_response_with_urls` | Happy-path upload |
| `should_set_sort_order_to_current_photo_count_on_upload` | sort_order = existing count |
| `should_throw_validation_exception_when_already_at_max_photos` | 8-photo limit |
| `should_throw_validation_exception_when_file_exceeds_size_limit` | 15 MB limit |
| `should_throw_validation_exception_when_file_is_not_an_image` | PDF rejected |
| `should_accept_png_and_webp_content_types` | Non-JPEG images accepted |
| `should_throw_not_found_when_item_does_not_exist_for_upload` | Item missing |
| `should_delete_photo_from_storage_and_repository` | Delete removes from storage + DB |
| `should_throw_not_found_when_photo_does_not_belong_to_item` | Wrong item/photo combination |
| `should_call_update_sort_order_for_each_item_in_request` | Reorder updates each record |
| `should_handle_empty_reorder_list_without_error` | Empty reorder list is a no-op |

---

## Known Limitations and Deferred Items

**P1 stories not yet implemented:**

- **US-105 (Edit item)**: No `PATCH /api/items/{id}` endpoint. Fields like name, rate, category cannot be changed post-creation.
- **US-106 (Adjust unit count)**: No dedicated endpoint to increase/decrease `quantity`. This matters when the shop acquires more units or retires damaged ones.

**Implementation notes and trade-offs:**

- The `ItemSummaryResponse` includes a `photoUrls` list (all full URLs) alongside `thumbnailUrl`. The feature story only calls for the thumbnail in the list view; the extra field is harmless but could be removed when the checkout team's requirements are finalised.
- `LocalImageStorageService` (dev profile) writes JPEG files locally regardless of input format. The `CloudflareR2ImageStorageService` (prod) converts to WebP via Thumbnailator. In tests, `ImageStorageService` is mocked so this divergence does not affect test coverage.
- `ItemPhotoController.listPhotos()` reads directly from `ItemPhotoRepository` (bypassing the service layer). This is intentional: it is a pure read with no business logic; routing it through a service would add indirection without value.

---

## Demo Walkthrough

Prerequisites: backend running on `http://localhost:8080` (dev profile), frontend on `http://localhost:5173`.

### 1. Authenticate

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | jq .data.token
```

Save the token as `TOKEN`.

### 2. Create an item (US-104)

```bash
curl -s -X POST http://localhost:8080/api/items \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Blue Sherwani",
    "category": "COSTUME",
    "size": "M",
    "rate": 200,
    "deposit": 1000,
    "quantity": 3
  }' | jq .
```

Verify: response is 201, `isActive: true`, `photos: []`.

### 3. Browse inventory (US-101)

```bash
curl -s "http://localhost:8080/api/items?page=0&size=20" \
  -H "Authorization: Bearer $TOKEN" | jq '.data.content[0]'
```

Verify: new item appears; `availableQuantity = 3`; `isAvailable = true`; `thumbnailUrl = null`.

### 4. Search and filter (US-102)

```bash
# By name
curl -s "http://localhost:8080/api/items?search=blue" \
  -H "Authorization: Bearer $TOKEN" | jq '.data.totalElements'

# By category
curl -s "http://localhost:8080/api/items?category=COSTUME" \
  -H "Authorization: Bearer $TOKEN" | jq '.data.content | length'

# No match
curl -s "http://localhost:8080/api/items?search=zzz" \
  -H "Authorization: Bearer $TOKEN" | jq '.data.totalElements'
# Expected: 0
```

### 5. Check availability (US-103)

```bash
ITEM_ID=<id from step 2>

# With date range
curl -s "http://localhost:8080/api/items/$ITEM_ID/availability?\
startDatetime=2026-05-01T10:00:00%2B05:30&\
endDatetime=2026-05-03T10:00:00%2B05:30" \
  -H "Authorization: Bearer $TOKEN" | jq .

# Without date range (general browse)
curl -s "http://localhost:8080/api/items/$ITEM_ID/availability" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

Verify: `availableQuantity = 3`, `isAvailable = true` (no bookings yet).

### 6. Upload a photo (US-107)

```bash
curl -s -X POST "http://localhost:8080/api/items/$ITEM_ID/photos" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/path/to/photo.jpg" | jq .
```

Verify: 201, `url` and `thumbnailUrl` are populated (local dev: `http://localhost:8080/uploads/...`).

Then browse again:
```bash
curl -s "http://localhost:8080/api/items/$ITEM_ID" \
  -H "Authorization: Bearer $TOKEN" | jq '.data.photos'
```

Verify: photo appears in `photos` array.

### 7. Validation errors (US-104 acceptance criteria)

```bash
# Rate = 0 → 400
curl -s -X POST http://localhost:8080/api/items \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"X","category":"DRESS","rate":0,"deposit":0,"quantity":1}' | jq .success
# Expected: false

# Blank name → 400
curl -s -X POST http://localhost:8080/api/items \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"","category":"DRESS","rate":100,"deposit":0,"quantity":1}' | jq .success
# Expected: false
```

### 8. Invalid availability date range (US-103)

```bash
curl -s "http://localhost:8080/api/items/$ITEM_ID/availability?\
startDatetime=2026-05-03T10:00:00%2B05:30&\
endDatetime=2026-05-01T10:00:00%2B05:30" \
  -H "Authorization: Bearer $TOKEN" | jq '{status: .success, error: .error}'
# Expected: success: false, error contains "endDatetime must be after startDatetime"
```
