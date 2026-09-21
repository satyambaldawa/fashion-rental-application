# US-801 / US-802 / US-803: Customer Reviews — Submit, Browse, Moderate

**Epic:** Customer Reviews
**Priority:** P1
**Depends On:** US-107 (image storage), 02-react-pwa-scaffold (PublicLayout, public routing)
**Blocks:** Nothing

---

## User Stories

**US-801:** As a customer, I can open a public link and submit a review — my name, mobile number, what I rented, a star rating, up to 256 characters of text, and up to 3 photos.
**US-802:** As a visitor, I can browse approved reviews on a public page, sorted by newest or highest rated, 10 per page. No reviewer's phone number is ever shown.
**US-803:** As the owner, I can see every submitted review in a moderation queue and approve, reject, or delete it. Nothing appears publicly until I approve it.

---

## Acceptance Criteria

**US-801 — Submit a review:**
- [ ] Public form at `/review` reachable without logging in
- [ ] Required fields: name (≤80 chars), mobile (10-digit Indian), item rented (free text, ≤100 chars), rating (1–5 stars), review text (≤256 chars)
- [ ] Character counter on the review text; the field hard-stops at 256
- [ ] Up to 3 images, optional. Each ≤5MB, JPEG/PNG/WebP only
- [ ] Images are stored resized and re-encoded, never at original size
- [ ] Submission is rate limited: 3 per IP per hour, 5 per phone per 24 hours
- [ ] Exceeding a limit returns HTTP 429 with a human-readable message
- [ ] On success the customer sees a confirmation that the review is awaiting approval

**US-802 — Browse reviews:**
- [ ] Public page at `/reviews` reachable without logging in
- [ ] Only `APPROVED` reviews are returned
- [ ] Sort selector with two modes: **Newest** and **Highest rated**
- [ ] Exactly 10 reviews per page, with pagination controls
- [ ] Each review shows: reviewer name, star rating, item rented, review text, submitted date, image thumbnails
- [ ] **The phone number is absent from the public API response entirely** — not hidden by CSS, not present in the JSON
- [ ] Empty state when no reviews are approved yet

**US-803 — Moderate reviews:**
- [ ] Owner-only page at `/reviews/manage`, guarded by `OwnerRoute`
- [ ] Filter by status: Pending / Approved / Rejected / All; defaults to Pending
- [ ] Owner sees the phone number (this view is authenticated and owner-only)
- [ ] Approve and Reject actions change status and stamp `moderated_at`
- [ ] Delete removes the review, its image rows, and the stored image objects
- [ ] `EXECUTIVE` role receives 403 on every `/api/reviews/**` route

---

## Key Decisions

| Question | Decision | Why |
|----------|----------|-----|
| How customers reach the form | **Open public URL** `/review` | Shareable by QR code, WhatsApp, or printed card without per-customer setup |
| Rating | **Required, 1–5 stars** | The "by rating" sort cannot exist without it |
| Visibility | **Owner approves first** — reviews land as `PENDING` | An open endpoint on a public shop site must never publish unreviewed text |
| Item rented | **Free text, ≤100 chars** | Keeps the public form decoupled from inventory; no public endpoint exposing the catalogue, and ad-hoc items still work |
| Rating filter | **Sort selector only** (Newest / Highest rated) | Matches the request; a star filter would add query permutations for little gain |
| Rate limiting | **Database count queries**, not an in-memory bucket | Survives restarts and multiple instances; two indexed counts are free at this traffic. No new dependency |
| Images of rejected reviews | **Uploaded on submit, deleted on reject/delete** | Simpler than staging blobs in Postgres; moderation window is short |
| Compression | **Reuse `ImageStorageService`** with a `reviews` namespace | Thumbnailator resize + re-encode and the full/thumbnail pair already exist. No new image code |

### Honest limitation

`X-Forwarded-For` is client-supplied and spoofable. The per-IP limit is a speed bump against
casual abuse, not a security boundary. **Moderation is the real control** — nothing reaches the
public page without the owner's approval, so the worst a determined spammer achieves is a noisy
queue and some storage cost.

---

## SQL Migration

`backend/src/main/resources/db/migration/V20260921001__create_reviews.sql`

```sql
CREATE TABLE reviews (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reviewer_name    VARCHAR(80)  NOT NULL,
    phone            VARCHAR(15)  NOT NULL,
    item_description VARCHAR(100) NOT NULL,
    rating           INTEGER      NOT NULL CHECK (rating BETWEEN 1 AND 5),
    review_text      VARCHAR(256) NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                     CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    submitter_ip     VARCHAR(45)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    moderated_at     TIMESTAMPTZ
);

CREATE TABLE review_images (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id     UUID NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
    url           TEXT NOT NULL,
    thumbnail_url TEXT NOT NULL,
    sort_order    INTEGER NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Public list: the two sort modes, both filtered to APPROVED
CREATE INDEX idx_reviews_status_created  ON reviews (status, created_at DESC);
CREATE INDEX idx_reviews_status_rating   ON reviews (status, rating DESC, created_at DESC);

-- Rate limiter lookups
CREATE INDEX idx_reviews_ip_created      ON reviews (submitter_ip, created_at);
CREATE INDEX idx_reviews_phone_created   ON reviews (phone, created_at);

CREATE INDEX idx_review_images_review_id ON review_images (review_id);
```

`phone` is intentionally **not** unique and **not** a foreign key to `customers` — a reviewer may
not be a registered customer, and the same person may review more than one rental.

`rating` is `INTEGER`, not `SMALLINT`, matching every other numeric column in this schema. With
`ddl-auto: validate` a `SMALLINT` column would force `Short` on the entity and a `Short`/`Integer`
conversion at the DTO boundary for no benefit. `CHECK` constraints are written inline on the
column, as in `V1__initial_schema.sql`.

---

## Backend Implementation

### Package layout

```
com.fashionrental.review/
  Review.java                       ← entity, nested Status enum
  ReviewImage.java                  ← entity, mirrors ItemPhoto
  ReviewRepository.java
  ReviewImageRepository.java
  ReviewService.java                ← orchestration only
  ReviewSubmissionGuard.java        ← rate limits; one reason to change
  ReviewImageUploader.java          ← file validation + storage calls
  ReviewMapper.java                 ← entity → DTO
  ReviewPublicController.java       ← /api/public/reviews
  ReviewAdminController.java        ← /api/reviews  (OWNER only)
  model/
    ReviewSort.java                 ← NEWEST | HIGHEST_RATED
    request/SubmitReviewRequest.java
    request/UpdateReviewStatusRequest.java
    response/SubmitReviewResponse.java
    response/PublicReviewResponse.java
    response/AdminReviewResponse.java
    response/ReviewImageResponse.java
```

Also added:
- `common/util/ClientIpResolver.java` — extracts the first hop of `X-Forwarded-For`, falling back
  to `request.getRemoteAddr()`
- `common/exception/RateLimitExceededException.java` + a handler in `GlobalExceptionHandler`
  returning **429**. A 400 would misreport a well-formed request as malformed.

### Entity

```java
@Entity
@Table(name = "reviews")
public class Review {

    public enum Status { PENDING, APPROVED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "reviewer_name", nullable = false, length = 80)
    private String reviewerName;

    @Column(nullable = false, length = 15)
    private String phone;

    @Column(name = "item_description", nullable = false, length = 100)
    private String itemDescription;

    @Column(nullable = false)
    private Integer rating;

    @Column(name = "review_text", nullable = false, length = 256)
    private String reviewText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "submitter_ip", nullable = false, length = 45)
    private String submitterIp;

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ReviewImage> images = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "moderated_at")
    private OffsetDateTime moderatedAt;

    @PrePersist void onCreate() { createdAt = OffsetDateTime.now(); updatedAt = createdAt; }
    @PreUpdate  void onUpdate() { updatedAt = OffsetDateTime.now(); }

    // getters / setters
}
```

`ReviewImage` mirrors `ItemPhoto` exactly: `id`, `@ManyToOne Review review`, `url`,
`thumbnailUrl`, `sortOrder`, `createdAt`.

### Repository

```java
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Page<Review> findByStatus(Review.Status status, Pageable pageable);

    long countBySubmitterIpAndCreatedAtAfter(String submitterIp, OffsetDateTime since);

    long countByPhoneAndCreatedAtAfter(String phone, OffsetDateTime since);
}
```

The unfiltered moderation query needs no declaration — `findAll(Pageable)` is inherited from
`JpaRepository`. Sorting is expressed through the `Pageable`, not through method names, so both
sort modes share one query:

- `NEWEST` → `Sort.by(DESC, "createdAt")`
- `HIGHEST_RATED` → `Sort.by(DESC, "rating").and(Sort.by(DESC, "createdAt"))`

`createdAt` is the tiebreaker on `HIGHEST_RATED` so pagination is stable — without it, equal
ratings can reshuffle between pages and a review can appear twice or not at all.

### ReviewSubmissionGuard

```java
@Component
public class ReviewSubmissionGuard {

    private static final int MAX_PER_IP_PER_HOUR = 3;
    private static final int MAX_PER_PHONE_PER_DAY = 5;

    private final ReviewRepository reviewRepository;
    private final Clock clock;

    public void checkSubmissionAllowed(String submitterIp, String phone) {
        OffsetDateTime now = OffsetDateTime.now(clock);

        if (reviewRepository.countBySubmitterIpAndCreatedAtAfter(submitterIp, now.minusHours(1))
                >= MAX_PER_IP_PER_HOUR) {
            throw new RateLimitExceededException(
                    "Too many reviews submitted. Please try again in an hour.");
        }

        if (reviewRepository.countByPhoneAndCreatedAtAfter(phone, now.minusDays(1))
                >= MAX_PER_PHONE_PER_DAY) {
            throw new RateLimitExceededException(
                    "This mobile number has reached today's review limit.");
        }
    }
}
```

`Clock` is injected so the limits are testable without sleeping. The bean already exists in
`config/ClockConfig.java` and is injected the same way in `CouponDiscountResolver`.

### ReviewImageUploader

```java
@Component
public class ReviewImageUploader {

    private static final String REVIEWS_NAMESPACE = "reviews";
    private static final int MAX_IMAGES = 3;
    private static final long MAX_BYTES_PER_IMAGE = 5L * 1024 * 1024;
    private static final List<String> ALLOWED_CONTENT_TYPES =
            List.of("image/jpeg", "image/png", "image/webp");

    /** Uploads every file, or throws. Callers MUST delete the returned results if the
     *  subsequent transaction fails — nothing here is transactional. */
    public List<UploadResult> uploadAll(MultipartFile[] files) { ... }

    public void deleteAll(List<UploadResult> uploaded) { ... }
}
```

Validation before any upload: count ≤ 3, each file non-empty, size ≤ 5MB, content type in the
allowlist. Violations throw `ValidationException` → 400.

Compression is whatever `ImageStorageService` already does — Thumbnailator resizes to the
configured max dimension and re-encodes, producing a full image plus a thumbnail. Reviews add no
image processing of their own.

### ReviewService

```java
@Service
@Transactional
public class ReviewService {

    public SubmitReviewResponse submit(SubmitReviewRequest request, MultipartFile[] images,
                                       String submitterIp) {
        reviewSubmissionGuard.checkSubmissionAllowed(submitterIp, request.phone());

        List<UploadResult> uploaded = reviewImageUploader.uploadAll(images);
        try {
            Review review = buildPendingReview(request, submitterIp, uploaded);
            return ReviewMapper.toSubmitResponse(reviewRepository.save(review));
        } catch (RuntimeException e) {
            reviewImageUploader.deleteAll(uploaded);   // never leave orphaned objects in R2
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Page<PublicReviewResponse> listPublic(ReviewSort sort, int page) { ... }

    @Transactional(readOnly = true)
    public Page<AdminReviewResponse> listForModeration(Review.Status status, int page, int size) { ... }

    public AdminReviewResponse updateStatus(UUID id, Review.Status status) { ... }

    public void delete(UUID id) { ... }   // deletes stored objects, then the row (images cascade)
}
```

`listPublic` hard-codes page size to 10 and status to `APPROVED`; neither is a caller parameter,
so no request can widen them.

`delete` removes the stored image objects **before** deleting the row. If object deletion fails,
the transaction rolls back and the review stays — better a stale row than an orphaned object with
no record of its URL.

### Mapper

`ReviewMapper` produces two distinct DTOs. `PublicReviewResponse` **has no phone field**, so the
public endpoint cannot leak a number even if the mapper is later edited carelessly:

```java
public record PublicReviewResponse(
        UUID id,
        String reviewerName,
        String itemDescription,
        int rating,
        String reviewText,
        OffsetDateTime createdAt,
        List<ReviewImageResponse> images
) {}

public record AdminReviewResponse(
        UUID id,
        String reviewerName,
        String phone,              // owner-only view
        String itemDescription,
        int rating,
        String reviewText,
        Review.Status status,
        OffsetDateTime createdAt,
        OffsetDateTime moderatedAt,
        List<ReviewImageResponse> images
) {}

public record ReviewImageResponse(UUID id, String url, String thumbnailUrl) {}

public record SubmitReviewResponse(UUID id, Review.Status status) {}
```

`SubmitReviewResponse` deliberately echoes nothing the submitter typed. It returns only the id
and `PENDING`, which is what the confirmation screen needs — a full review body in the response
would imply the review is live.

### Request DTOs

```java
public record SubmitReviewRequest(
        @NotBlank @Size(max = 80)
        String reviewerName,

        @NotBlank
        @Pattern(regexp = "^[6-9]\\d{9}$", message = "Enter a valid 10-digit Indian mobile number")
        String phone,

        @NotBlank @Size(max = 100)
        String itemDescription,

        @NotNull @Min(1) @Max(5)
        Integer rating,

        @NotBlank @Size(max = 256)
        String reviewText
) {}

public record UpdateReviewStatusRequest(@NotNull Review.Status status) {}
```

---

## API Endpoints

### Public (no authentication)

**`POST /api/public/reviews`** — `multipart/form-data`

| Part | Type | Required |
|------|------|----------|
| `review` | `application/json` → `SubmitReviewRequest` | yes |
| `images` | file × 0–3 | no |

```java
@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<ApiResponse<SubmitReviewResponse>> submitReview(
        @Valid @RequestPart("review") SubmitReviewRequest request,
        @RequestPart(value = "images", required = false) MultipartFile[] images,
        HttpServletRequest httpRequest) {

    String ip = ClientIpResolver.resolve(httpRequest);
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(reviewService.submit(request, images, ip)));
}
```

`201` → `{ "success": true, "data": { "id": "...", "status": "PENDING" } }`
`400` validation failure · `429` rate limit exceeded

**`GET /api/public/reviews?sort=NEWEST&page=0`**

`sort` defaults to `NEWEST`. Page size is fixed at 10 server-side and is not a parameter.

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "...",
        "reviewerName": "Priya S",
        "itemDescription": "Red bridal lehenga",
        "rating": 5,
        "reviewText": "Beautiful outfit, fit perfectly. Staff were very helpful.",
        "createdAt": "2026-09-20T14:30:00+05:30",
        "images": [{ "id": "...", "url": "...", "thumbnailUrl": "..." }]
      }
    ],
    "totalElements": 34, "totalPages": 4, "number": 0, "size": 10
  },
  "error": null
}
```

### Admin (OWNER only)

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/reviews?status=PENDING&page=0&size=20` | Moderation queue; `status` omitted → all |
| `PATCH` | `/api/reviews/{id}/status` | Body `UpdateReviewStatusRequest`; stamps `moderated_at` |
| `DELETE` | `/api/reviews/{id}` | Removes row, image rows, and stored objects |

### SecurityConfig

One line, placed **before** the `/api/**` catch-all:

```java
.requestMatchers("/api/reviews", "/api/reviews/**").hasRole("OWNER")
```

`/api/public/**` is already `permitAll()`, so the public routes need no change.

### Swagger

`@Tag(name = "Reviews")` on both controllers; `@Operation(summary = "...")` on every method.

---

## Frontend Implementation

### Types — `src/types/review.ts`

```ts
export type ReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
export type ReviewSort = 'NEWEST' | 'HIGHEST_RATED'

export interface ReviewImage { id: string; url: string; thumbnailUrl: string }

export interface PublicReview {
  id: string
  reviewerName: string
  itemDescription: string
  rating: number
  reviewText: string
  createdAt: string
  images: ReviewImage[]
}

export interface AdminReview extends PublicReview {
  phone: string
  status: ReviewStatus
  moderatedAt: string | null
}

export interface SubmitReviewRequest {
  reviewerName: string
  phone: string
  itemDescription: string
  rating: number
  reviewText: string
}
```

**Targeted cleanup:** move `PageResult<T>` from `src/types/inventory.ts` to `src/types/api.ts`,
where the shared envelope types belong. Only two files import it today
(`types/inventory.ts`, `api/items.ts`), so this is a two-line change and stops reviews from
importing a pagination type out of the inventory domain.

### API — `src/api/reviews.ts`

Public calls use `publicClient`; admin calls use the authenticated `client`.

```ts
export const reviewsApi = {
  submit: (data: SubmitReviewRequest, images: File[]) => {
    const form = new FormData()
    form.append('review', new Blob([JSON.stringify(data)], { type: 'application/json' }))
    images.forEach(f => form.append('images', f))
    return publicClient
      .post<ApiResponse<{ id: string; status: ReviewStatus }>>('/reviews', form)
      .then(r => r.data.data!)
  },

  listPublic: (params: { sort: ReviewSort; page: number }) =>
    publicClient.get<ApiResponse<PageResult<PublicReview>>>('/reviews', { params })
      .then(r => r.data.data!),

  listForModeration: (params: { status?: ReviewStatus; page: number; size: number }) =>
    client.get<ApiResponse<PageResult<AdminReview>>>('/reviews', { params })
      .then(r => r.data.data!),

  updateStatus: (id: string, status: ReviewStatus) =>
    client.patch<ApiResponse<AdminReview>>(`/reviews/${id}/status`, { status })
      .then(r => r.data.data!),

  remove: (id: string) => client.delete(`/reviews/${id}`).then(() => undefined),
}
```

The `Content-Type` header must be left unset on the `submit` call so the browser adds the
multipart boundary. `publicClient` sets `application/json` by default, so pass
`{ headers: { 'Content-Type': undefined } }`.

### Pages

**`src/pages/public/SubmitReviewPage.tsx`** — route `/review`, wrapped in `PublicLayout`.

AntD `Form` with `Input` (name), `Input` (phone, `maxLength=10`), `Input` (item),
`Rate` (required, 1–5), `Input.TextArea` with `maxLength={256}` and `showCount`, and an
`Upload` with `listType="picture-card"`, `maxCount={3}`, `beforeUpload` returning `false` so files
are held client-side until submit. Client-side `beforeUpload` also rejects files over 5MB and
wrong types, mirroring the server rules so the customer gets an instant message.

On success, swap the form for an AntD `Result` reading "Thank you — your review will appear once
it has been approved." On 429, surface the server's message verbatim.

**`src/pages/public/ReviewsPage.tsx`** — route `/reviews`, wrapped in `PublicLayout`.

`Select` for the sort mode, a list of review `Card`s (name, `Rate` in read-only mode, item,
text, relative date, thumbnail row opening AntD `Image.PreviewGroup`), and `Pagination` with
`pageSize={10}` and `showSizeChanger={false}`. `useQuery` keyed on `['reviews', sort, page]`.
`Empty` state when `totalElements === 0`.

**`src/pages/reviews/ReviewModerationPage.tsx`** — route `/reviews/manage` inside `AppLayout`,
wrapped in `OwnerRoute` exactly as `/gallery/manage` is.

AntD `Table` with a status filter defaulting to Pending; columns for name, phone, item, rating,
text, submitted date, images, and actions. Approve / Reject / Delete buttons invalidate the
query on success. Delete uses AntD `Popconfirm`, not a native `confirm()`.

### Navigation

- Add `{ key: '/reviews', label: 'Reviews', target: '/reviews' }` to `PUBLIC_NAV_ITEMS` in
  `Sidebar.tsx` so logged-out visitors see Gallery / **Reviews** / Login / New Rental.
- Add a Reviews entry to the staff nav, and a Manage Reviews entry to the owner-only section
  alongside Manage Gallery.

### Routing — `App.tsx`

```tsx
<Route path="/review"  element={<PublicLayout><SubmitReviewPage /></PublicLayout>} />
<Route path="/reviews" element={<PublicLayout><ReviewsPage /></PublicLayout>} />
```

Both sit beside the existing `/gallery` route, before the `/*` protected catch-all.
`/reviews/manage` is registered inside `AppLayout`'s nested routes, not here.

---

## Test Cases

### Backend unit tests

`ReviewSubmissionGuardTest`
- `should_allow_submission_when_no_prior_reviews_from_ip`
- `should_reject_when_ip_has_three_reviews_within_the_last_hour`
- `should_allow_when_ips_three_reviews_are_older_than_one_hour`
- `should_reject_when_phone_has_five_reviews_within_twenty_four_hours`
- `should_report_the_phone_limit_message_when_phone_limit_is_hit_first`

`ReviewImageUploaderTest`
- `should_reject_when_more_than_three_images_are_supplied`
- `should_reject_when_an_image_exceeds_five_megabytes`
- `should_reject_when_content_type_is_not_an_allowed_image_type`
- `should_upload_every_file_under_the_reviews_namespace`
- `should_delete_all_uploaded_objects_when_asked`

`ReviewServiceTest`
- `should_persist_new_review_with_pending_status`
- `should_delete_uploaded_images_when_persisting_the_review_fails`
- `should_stamp_moderated_at_when_status_changes_to_approved`
- `should_delete_stored_objects_before_deleting_the_review`

`ReviewMapperTest`
- `should_omit_phone_from_public_response` — asserts on the record components, so the test fails
  at compile time if a `phone` field is ever added to `PublicReviewResponse`
- `should_include_phone_in_admin_response`

`SubmitReviewRequestValidationTest`
- `should_reject_review_text_longer_than_256_characters`
- `should_reject_rating_of_zero_and_rating_of_six`
- `should_reject_phone_that_is_not_a_ten_digit_indian_mobile_number`
- `should_reject_blank_reviewer_name`

`ReviewPublicControllerTest` (MockMvc)
- `should_return_429_when_rate_limit_is_exceeded`
- `should_return_201_with_pending_status_on_successful_submission`
- `should_default_sort_to_newest_when_sort_parameter_is_absent`

`ReviewAdminControllerTest` (MockMvc)
- `should_return_403_for_executive_role_on_moderation_queue`
- `should_return_403_for_executive_role_on_status_update`
- `should_return_401_when_unauthenticated`

### Backend integration tests (`src/integrationTest`, `*IT.java`)

`ReviewRepositoryIT`
- `should_return_only_approved_reviews_on_the_public_query`
- `should_return_ten_reviews_per_page` — seed 25, assert `totalPages == 3` and page sizes 10/10/5
- `should_order_by_rating_then_created_at_when_sorting_by_highest_rated` — seed reviews with
  duplicate ratings and assert no review appears on two pages
- `should_count_only_reviews_inside_the_rate_limit_window`
- `should_cascade_delete_review_images_when_the_review_is_deleted`

### Frontend tests (Vitest + RTL, msw)

`SubmitReviewPage.test.tsx`
- `shows a character count and stops accepting input at 256 characters`
- `blocks submission when the mobile number is not ten digits`
- `blocks submission when no rating is selected`
- `rejects a fourth image`
- `rejects an image larger than 5MB with an inline message`
- `shows the awaiting-approval confirmation after a successful submit`
- `surfaces the server message when the API responds 429`

`ReviewsPage.test.tsx`
- `renders ten reviews on the first page`
- `refetches with HIGHEST_RATED when the sort is changed`
- `requests the next page when pagination is clicked`
- `never renders a phone number` — asserts no 10-digit numeric string appears in the DOM
- `shows the empty state when no reviews are approved`

`ReviewModerationPage.test.tsx`
- `defaults the status filter to Pending`
- `approving a review refetches the queue`
- `shows the reviewer phone number to the owner`

### Not covered

No e2e test. The existing Playwright suite is deliberately limited to the core rental flows, and
reviews are not on the money path. No average-rating summary endpoint — YAGNI until asked for.

---

## Build Order

1. Migration `V20260921001__create_reviews.sql`
2. Entities, repositories, `RateLimitExceededException`, `ClientIpResolver`
3. `ReviewSubmissionGuard` + `ReviewImageUploader` with their unit tests
4. `ReviewService` + `ReviewMapper` + DTOs
5. Controllers + `SecurityConfig` line + controller tests
6. Integration tests
7. `PageResult` move, `types/review.ts`, `api/reviews.ts`
8. `ReviewsPage` (read path first — verifiable against seeded data)
9. `SubmitReviewPage`
10. `ReviewModerationPage`, nav entries, routing
