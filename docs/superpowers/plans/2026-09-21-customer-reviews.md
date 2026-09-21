# Customer Reviews Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let customers submit a rated, photo-bearing review through a public link, and let visitors browse owner-approved reviews on a paginated public page — with the reviewer's phone number never leaving the server.

**Architecture:** A new `com.fashionrental.review` module following the gallery module's shape: entity + child image table, a service that orchestrates, and two controllers (public unauthenticated, admin OWNER-only). Every review lands as `PENDING` and is invisible until the owner approves it. Abuse control is two indexed `COUNT` queries rather than an in-memory bucket, so limits survive restarts. Image compression reuses the existing `ImageStorageService`, which already resizes and re-encodes through Thumbnailator.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, Flyway, PostgreSQL, JUnit 5 + Mockito, Testcontainers; React 18, TypeScript, Vite, Ant Design, TanStack Query, Zustand, Vitest + React Testing Library + msw.

**Spec:** `features/07-reviews/US-801-803-customer-reviews.md`

## Global Constraints

- **Money and counts are `INTEGER` in SQL, `int`/`Integer` in Java.** No `float`, no `double`, no `DECIMAL`, no `SMALLINT`.
- **All datetimes are `TIMESTAMPTZ` in SQL and `OffsetDateTime` in Java.** Never `LocalDateTime`.
- **Every controller method returns `ResponseEntity<ApiResponse<XxxResponse>>`.** Never `Map`, never a raw type, never a primitive.
- **Every `@RequestBody` / `@RequestPart` JSON parameter is a typed record from the module's `model/request` package, annotated `@Valid`**, with Bean Validation annotations on every field.
- **Flyway naming:** `V<YYYYMMDD><NNN>__<description>.sql`. This feature uses exactly one: `V20260921001__create_reviews.sql`.
- **Hibernate runs `ddl-auto: validate`.** An entity that disagrees with the migration stops the app from starting. Only `ContextLoadsSmokeIT` proves the two agree — a green unit test does not.
- **Test names are specifications:** `should_reject_when_ip_has_three_reviews_within_the_last_hour`, not `testRateLimit`.
- **No inline comments explaining *what* code does.** Only *why*, and contract warnings.
- **Public review responses must not contain a phone field.** This is the feature's one security-relevant invariant.
- **Page size on the public review list is fixed at 10 server-side** and is not a request parameter.
- **Frontend money display** uses `formatCurrency` from `src/utils/currency.ts`. (Not used by this feature — no amounts are displayed — but noted so no one invents a second formatter.)
- **Ask before committing.** Per `CLAUDE.md`, every commit, push, and PR needs the user's explicit approval. The commit steps below are the *content* of those commits, not permission to make them unattended.

---

## File Structure

**Backend — created**

| File | Responsibility |
|------|----------------|
| `db/migration/V20260921001__create_reviews.sql` | `reviews` + `review_images` tables and their indexes |
| `review/Review.java` | Review entity, nested `Status` enum |
| `review/ReviewImage.java` | Child image entity, mirrors `ItemPhoto` |
| `review/ReviewRepository.java` | Paged lookups + the two rate-limit counts |
| `review/ReviewImageRepository.java` | Child-row access |
| `review/ReviewSubmissionGuard.java` | Rate limits. One reason to change |
| `review/ReviewImageUploader.java` | File validation + storage calls. One reason to change |
| `review/ReviewMapper.java` | Entity → DTO. No logic beyond mapping |
| `review/ReviewService.java` | Orchestration only |
| `review/ReviewPublicController.java` | `/api/public/reviews` |
| `review/ReviewAdminController.java` | `/api/reviews`, OWNER-only |
| `review/model/ReviewSort.java` | `NEWEST` \| `HIGHEST_RATED` |
| `review/model/request/SubmitReviewRequest.java` | Validated submit payload |
| `review/model/request/UpdateReviewStatusRequest.java` | Moderation payload |
| `review/model/response/SubmitReviewResponse.java` | `{ id, status }` only |
| `review/model/response/PublicReviewResponse.java` | **No phone component** |
| `review/model/response/AdminReviewResponse.java` | Includes phone |
| `review/model/response/ReviewImageResponse.java` | `{ id, url, thumbnailUrl }` |
| `common/util/ClientIpResolver.java` | First hop of `X-Forwarded-For`, else `getRemoteAddr()` |
| `common/exception/RateLimitExceededException.java` | Maps to HTTP 429 |

**Backend — modified**

| File | Change |
|------|--------|
| `common/exception/GlobalExceptionHandler.java` | One handler returning 429 |
| `config/SecurityConfig.java` | One `.requestMatchers("/api/reviews", "/api/reviews/**").hasRole("OWNER")` line |

**Frontend — created**

| File | Responsibility |
|------|----------------|
| `src/types/review.ts` | Every review type |
| `src/api/reviews.ts` | Public + admin API functions |
| `src/pages/public/ReviewsPage.tsx` | Public browse, sort + pagination |
| `src/pages/public/SubmitReviewPage.tsx` | Public submit form |
| `src/pages/reviews/ReviewModerationPage.tsx` | Owner moderation queue |

**Frontend — modified**

| File | Change |
|------|--------|
| `src/types/api.ts` | Gains `PageResult<T>` |
| `src/types/inventory.ts` | Loses `PageResult<T>`, re-exports nothing |
| `src/api/items.ts` | Imports `PageResult` from `types/api` |
| `src/App.tsx` | `/review` and `/reviews` routes |
| `src/components/layout/AppLayout.tsx` | `/reviews/manage` route inside `OwnerRoute` |
| `src/components/layout/Sidebar.tsx` | Nav entries |
| `src/test/handlers.ts` | Default msw handlers for review endpoints |

---

### Task 1: Schema and entities

**Files:**
- Create: `backend/src/main/resources/db/migration/V20260921001__create_reviews.sql`
- Create: `backend/src/main/java/com/fashionrental/review/Review.java`
- Create: `backend/src/main/java/com/fashionrental/review/ReviewImage.java`
- Create: `backend/src/main/java/com/fashionrental/review/ReviewRepository.java`
- Create: `backend/src/main/java/com/fashionrental/review/ReviewImageRepository.java`
- Test: `backend/src/integrationTest/java/com/fashionrental/ContextLoadsSmokeIT.java` (existing — run, do not edit)

**Interfaces:**
- Consumes: nothing.
- Produces: `Review` (with `Review.Status { PENDING, APPROVED, REJECTED }`), `ReviewImage`, and
  `ReviewRepository` exposing `findByStatus(Review.Status, Pageable) → Page<Review>`,
  `countBySubmitterIpAndCreatedAtAfter(String, OffsetDateTime) → long`,
  `countByPhoneAndCreatedAtAfter(String, OffsetDateTime) → long`, plus everything inherited from
  `JpaRepository<Review, UUID>` (notably `findAll(Pageable)`).

- [ ] **Step 1: Write the migration**

`V20260921001__create_reviews.sql`:

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

CREATE INDEX idx_reviews_status_created  ON reviews (status, created_at DESC);
CREATE INDEX idx_reviews_status_rating   ON reviews (status, rating DESC, created_at DESC);
CREATE INDEX idx_reviews_ip_created      ON reviews (submitter_ip, created_at);
CREATE INDEX idx_reviews_phone_created   ON reviews (phone, created_at);
CREATE INDEX idx_review_images_review_id ON review_images (review_id);
```

`gen_random_uuid()` is available because `V1__initial_schema.sql` already does
`CREATE EXTENSION IF NOT EXISTS "pgcrypto"`.

- [ ] **Step 2: Write `Review.java`**

```java
package com.fashionrental.review;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public void addImage(ReviewImage image) {
        image.setReview(this);
        images.add(image);
    }

    public UUID getId() { return id; }
    public String getReviewerName() { return reviewerName; }
    public void setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getItemDescription() { return itemDescription; }
    public void setItemDescription(String itemDescription) { this.itemDescription = itemDescription; }
    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public String getReviewText() { return reviewText; }
    public void setReviewText(String reviewText) { this.reviewText = reviewText; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getSubmitterIp() { return submitterIp; }
    public void setSubmitterIp(String submitterIp) { this.submitterIp = submitterIp; }
    public List<ReviewImage> getImages() { return images; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getModeratedAt() { return moderatedAt; }
    public void setModeratedAt(OffsetDateTime moderatedAt) { this.moderatedAt = moderatedAt; }
}
```

- [ ] **Step 3: Write `ReviewImage.java`**

```java
package com.fashionrental.review;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "review_images")
public class ReviewImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String url;

    @Column(name = "thumbnail_url", nullable = false, columnDefinition = "TEXT")
    private String thumbnailUrl;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public Review getReview() { return review; }
    public void setReview(Review review) { this.review = review; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 4: Write the repositories**

```java
package com.fashionrental.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Page<Review> findByStatus(Review.Status status, Pageable pageable);

    long countBySubmitterIpAndCreatedAtAfter(String submitterIp, OffsetDateTime since);

    long countByPhoneAndCreatedAtAfter(String phone, OffsetDateTime since);
}
```

```java
package com.fashionrental.review;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReviewImageRepository extends JpaRepository<ReviewImage, UUID> {
}
```

Do **not** declare `findAllBy(Pageable)` — `findAll(Pageable)` is already inherited.

- [ ] **Step 5: Run the smoke IT to prove entity and schema agree**

Run: `cd backend && ./gradlew integrationTest --tests com.fashionrental.ContextLoadsSmokeIT`
Expected: PASS. A failure here means an entity field disagrees with a column under
`ddl-auto: validate` — read the Hibernate schema-validation message, which names the exact
column, and fix the entity or the migration before moving on.

- [ ] **Step 6: Run the full backend suite**

Run: `cd backend && ./gradlew test`
Expected: PASS, unchanged from before this task.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/resources/db/migration/V20260921001__create_reviews.sql \
        backend/src/main/java/com/fashionrental/review/
git commit -m "feat(review): add reviews and review_images schema with entities"
```

---

### Task 2: 429 plumbing and client IP resolution

**Files:**
- Create: `backend/src/main/java/com/fashionrental/common/exception/RateLimitExceededException.java`
- Create: `backend/src/main/java/com/fashionrental/common/util/ClientIpResolver.java`
- Modify: `backend/src/main/java/com/fashionrental/common/exception/GlobalExceptionHandler.java`
- Test: `backend/src/test/java/com/fashionrental/common/util/ClientIpResolverTest.java`

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces: `RateLimitExceededException(String message)` extending `RuntimeException`, mapped to
  HTTP 429; `ClientIpResolver.resolve(HttpServletRequest) → String` (static).

- [ ] **Step 1: Write the failing test**

`ClientIpResolverTest.java`:

```java
package com.fashionrental.common.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    @Test
    void should_use_remote_addr_when_no_forwarded_header_is_present() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void should_use_first_hop_when_forwarded_header_lists_several_proxies() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.7, 70.41.3.18, 150.172.238.178");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("203.0.113.7");
    }

    @Test
    void should_fall_back_to_remote_addr_when_forwarded_header_is_blank() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "   ");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("10.0.0.1");
    }

    @Test
    void should_truncate_to_the_column_width_when_a_header_is_absurdly_long() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "x".repeat(200));

        assertThat(ClientIpResolver.resolve(request)).hasSize(45);
    }
}
```

The truncation test exists because `submitter_ip` is `VARCHAR(45)` and the header is
attacker-controlled. Without truncation a long header turns a rate-limit check into a 500 from a
database constraint violation.

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && ./gradlew test --tests com.fashionrental.common.util.ClientIpResolverTest`
Expected: FAIL — compilation error, `ClientIpResolver` does not exist.

- [ ] **Step 3: Write `ClientIpResolver`**

```java
package com.fashionrental.common.util;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIpResolver {

    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final int MAX_LENGTH = 45;

    private ClientIpResolver() {
    }

    /**
     * Contract warning: the returned value is attacker-controlled when the request arrives
     * through a proxy that forwards a client-supplied X-Forwarded-For. Use it for abuse
     * throttling only — never for authorization.
     */
    public static String resolve(HttpServletRequest request) {
        String forwardedFor = request.getHeader(FORWARDED_FOR);
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return truncate(request.getRemoteAddr());
        }
        String firstHop = forwardedFor.split(",")[0].trim();
        return firstHop.isEmpty() ? truncate(request.getRemoteAddr()) : truncate(firstHop);
    }

    private static String truncate(String value) {
        return value.length() <= MAX_LENGTH ? value : value.substring(0, MAX_LENGTH);
    }
}
```

- [ ] **Step 4: Write `RateLimitExceededException`**

```java
package com.fashionrental.common.exception;

public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
```

- [ ] **Step 5: Add the handler to `GlobalExceptionHandler`**

Insert after the existing `handleValidation(ValidationException)` method:

```java
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimitExceeded(RateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiResponse.error(ex.getMessage()));
    }
```

429 rather than 400: the request is well-formed, and reporting it as malformed would send the
client looking for a field to fix.

- [ ] **Step 6: Run the test to verify it passes**

Run: `cd backend && ./gradlew test --tests com.fashionrental.common.util.ClientIpResolverTest`
Expected: PASS, 4 tests.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/fashionrental/common/ \
        backend/src/test/java/com/fashionrental/common/util/ClientIpResolverTest.java
git commit -m "feat(common): add rate-limit exception mapped to 429 and client IP resolver"
```

---

### Task 3: ReviewSubmissionGuard

**Files:**
- Create: `backend/src/main/java/com/fashionrental/review/ReviewSubmissionGuard.java`
- Test: `backend/src/test/java/com/fashionrental/review/ReviewSubmissionGuardTest.java`

**Interfaces:**
- Consumes: `ReviewRepository` (Task 1), `RateLimitExceededException` (Task 2), and the existing
  `Clock` bean from `config/ClockConfig.java`.
- Produces: `ReviewSubmissionGuard.checkSubmissionAllowed(String submitterIp, String phone) → void`,
  throwing `RateLimitExceededException`.

- [ ] **Step 1: Write the failing test**

```java
package com.fashionrental.review;

import com.fashionrental.common.exception.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewSubmissionGuardTest {

    private static final String IP = "203.0.113.7";
    private static final String PHONE = "9876543210";
    private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");

    @Mock
    private ReviewRepository reviewRepository;

    private ReviewSubmissionGuard guard;

    @BeforeEach
    void setUp() {
        guard = new ReviewSubmissionGuard(reviewRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void should_allow_submission_when_no_prior_reviews_exist() {
        when(reviewRepository.countBySubmitterIpAndCreatedAtAfter(eq(IP), any())).thenReturn(0L);
        when(reviewRepository.countByPhoneAndCreatedAtAfter(eq(PHONE), any())).thenReturn(0L);

        assertThatCode(() -> guard.checkSubmissionAllowed(IP, PHONE)).doesNotThrowAnyException();
    }

    @Test
    void should_reject_when_ip_has_three_reviews_within_the_last_hour() {
        when(reviewRepository.countBySubmitterIpAndCreatedAtAfter(eq(IP), any())).thenReturn(3L);

        assertThatThrownBy(() -> guard.checkSubmissionAllowed(IP, PHONE))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("try again in an hour");
    }

    @Test
    void should_count_the_ip_window_from_exactly_one_hour_before_now() {
        OffsetDateTime expectedWindowStart = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).minusHours(1);
        when(reviewRepository.countBySubmitterIpAndCreatedAtAfter(IP, expectedWindowStart)).thenReturn(0L);
        when(reviewRepository.countByPhoneAndCreatedAtAfter(eq(PHONE), any())).thenReturn(0L);

        assertThatCode(() -> guard.checkSubmissionAllowed(IP, PHONE)).doesNotThrowAnyException();
    }

    @Test
    void should_reject_when_phone_has_five_reviews_within_twenty_four_hours() {
        when(reviewRepository.countBySubmitterIpAndCreatedAtAfter(eq(IP), any())).thenReturn(0L);
        when(reviewRepository.countByPhoneAndCreatedAtAfter(eq(PHONE), any())).thenReturn(5L);

        assertThatThrownBy(() -> guard.checkSubmissionAllowed(IP, PHONE))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("mobile number");
    }

    @Test
    void should_report_the_ip_limit_when_both_limits_are_exceeded() {
        when(reviewRepository.countBySubmitterIpAndCreatedAtAfter(eq(IP), any())).thenReturn(9L);

        assertThatThrownBy(() -> guard.checkSubmissionAllowed(IP, PHONE))
                .hasMessageContaining("try again in an hour");
    }
}
```

`should_count_the_ip_window_from_exactly_one_hour_before_now` uses a strict argument match on the
timestamp, so an off-by-one-unit bug (`minusMinutes(1)`, `minusDays(1)`) makes the stub miss and
the test fail. Without it, `any()` would let a wrong window through.

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && ./gradlew test --tests com.fashionrental.review.ReviewSubmissionGuardTest`
Expected: FAIL — compilation error, `ReviewSubmissionGuard` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.fashionrental.review;

import com.fashionrental.common.exception.RateLimitExceededException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;

@Component
public class ReviewSubmissionGuard {

    private static final int MAX_PER_IP_PER_HOUR = 3;
    private static final int MAX_PER_PHONE_PER_DAY = 5;

    private final ReviewRepository reviewRepository;
    private final Clock clock;

    public ReviewSubmissionGuard(ReviewRepository reviewRepository, Clock clock) {
        this.reviewRepository = reviewRepository;
        this.clock = clock;
    }

    public void checkSubmissionAllowed(String submitterIp, String phone) {
        OffsetDateTime now = OffsetDateTime.now(clock);

        if (reviewRepository.countBySubmitterIpAndCreatedAtAfter(submitterIp, now.minusHours(1))
                >= MAX_PER_IP_PER_HOUR) {
            throw new RateLimitExceededException("Too many reviews submitted. Please try again in an hour.");
        }

        if (reviewRepository.countByPhoneAndCreatedAtAfter(phone, now.minusDays(1))
                >= MAX_PER_PHONE_PER_DAY) {
            throw new RateLimitExceededException("This mobile number has reached today's review limit.");
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd backend && ./gradlew test --tests com.fashionrental.review.ReviewSubmissionGuardTest`
Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/fashionrental/review/ReviewSubmissionGuard.java \
        backend/src/test/java/com/fashionrental/review/ReviewSubmissionGuardTest.java
git commit -m "feat(review): rate limit submissions per IP and per phone"
```

---

### Task 4: ReviewImageUploader

**Files:**
- Create: `backend/src/main/java/com/fashionrental/review/ReviewImageUploader.java`
- Test: `backend/src/test/java/com/fashionrental/review/ReviewImageUploaderTest.java`

**Interfaces:**
- Consumes: the existing `ImageStorageService` and `UploadResult(String fullUrl, String thumbnailUrl)`
  from `com.fashionrental.inventory.storage`.
- Produces: `ReviewImageUploader.uploadAll(MultipartFile[] files) → List<UploadResult>` (empty list
  for `null` or empty input) and `ReviewImageUploader.deleteAll(List<UploadResult> uploaded) → void`.

- [ ] **Step 1: Write the failing test**

```java
package com.fashionrental.review;

import com.fashionrental.common.exception.ValidationException;
import com.fashionrental.inventory.storage.ImageStorageService;
import com.fashionrental.inventory.storage.UploadResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewImageUploaderTest {

    @Mock
    private ImageStorageService imageStorageService;

    @InjectMocks
    private ReviewImageUploader uploader;

    private static MockMultipartFile jpeg(String name, int sizeBytes) {
        return new MockMultipartFile(name, name + ".jpg", "image/jpeg", new byte[sizeBytes]);
    }

    @Test
    void should_return_empty_list_when_no_files_are_supplied() {
        assertThat(uploader.uploadAll(null)).isEmpty();
        assertThat(uploader.uploadAll(new MultipartFile[0])).isEmpty();
    }

    @Test
    void should_reject_when_more_than_three_images_are_supplied() {
        MultipartFile[] files = { jpeg("a", 10), jpeg("b", 10), jpeg("c", 10), jpeg("d", 10) };

        assertThatThrownBy(() -> uploader.uploadAll(files))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("at most 3");
    }

    @Test
    void should_reject_when_an_image_exceeds_five_megabytes() {
        MultipartFile[] files = { jpeg("big", 5 * 1024 * 1024 + 1) };

        assertThatThrownBy(() -> uploader.uploadAll(files))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("5MB");
    }

    @Test
    void should_reject_when_content_type_is_not_an_allowed_image_type() {
        MultipartFile[] files = {
                new MockMultipartFile("doc", "doc.pdf", "application/pdf", new byte[10])
        };

        assertThatThrownBy(() -> uploader.uploadAll(files))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("JPEG");
    }

    @Test
    void should_validate_every_file_before_uploading_any() throws IOException {
        MultipartFile[] files = { jpeg("ok", 10), jpeg("big", 5 * 1024 * 1024 + 1) };

        assertThatThrownBy(() -> uploader.uploadAll(files)).isInstanceOf(ValidationException.class);

        verify(imageStorageService, never()).uploadImage(anyString(), any(), any(), any(), anyLong());
    }

    @Test
    void should_upload_every_file_under_the_reviews_namespace() throws IOException {
        when(imageStorageService.uploadImage(eq("reviews"), any(), any(), any(), anyLong()))
                .thenReturn(new UploadResult("full-1", "thumb-1"), new UploadResult("full-2", "thumb-2"));

        List<UploadResult> results = uploader.uploadAll(new MultipartFile[] { jpeg("a", 10), jpeg("b", 10) });

        assertThat(results).containsExactly(
                new UploadResult("full-1", "thumb-1"),
                new UploadResult("full-2", "thumb-2"));
    }

    @Test
    void should_delete_every_uploaded_object_when_asked() {
        uploader.deleteAll(List.of(new UploadResult("full-1", "thumb-1"), new UploadResult("full-2", "thumb-2")));

        verify(imageStorageService).deleteImage("full-1", "thumb-1");
        verify(imageStorageService).deleteImage("full-2", "thumb-2");
    }
}
```

`should_validate_every_file_before_uploading_any` is the one that matters: validating lazily
inside the upload loop means a rejected fourth file leaves three orphaned objects in R2.

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && ./gradlew test --tests com.fashionrental.review.ReviewImageUploaderTest`
Expected: FAIL — compilation error, `ReviewImageUploader` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.fashionrental.review;

import com.fashionrental.common.exception.ValidationException;
import com.fashionrental.inventory.storage.ImageStorageService;
import com.fashionrental.inventory.storage.UploadResult;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class ReviewImageUploader {

    private static final String REVIEWS_NAMESPACE = "reviews";
    private static final int MAX_IMAGES = 3;
    private static final long MAX_BYTES_PER_IMAGE = 5L * 1024 * 1024;
    private static final List<String> ALLOWED_CONTENT_TYPES =
            List.of("image/jpeg", "image/png", "image/webp");

    private final ImageStorageService imageStorageService;

    public ReviewImageUploader(ImageStorageService imageStorageService) {
        this.imageStorageService = imageStorageService;
    }

    /**
     * Contract warning: this is not transactional. If the caller's transaction later fails it
     * MUST call {@link #deleteAll} with the returned results, or the stored objects are orphaned.
     */
    public List<UploadResult> uploadAll(MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return List.of();
        }
        validateAll(files);

        List<UploadResult> uploaded = new ArrayList<>();
        for (MultipartFile file : files) {
            try {
                uploaded.add(imageStorageService.uploadImage(
                        REVIEWS_NAMESPACE,
                        UUID.randomUUID(),
                        file.getInputStream(),
                        file.getOriginalFilename(),
                        file.getSize()));
            } catch (IOException e) {
                deleteAll(uploaded);
                throw new IllegalStateException("Failed to store review image", e);
            }
        }
        return uploaded;
    }

    public void deleteAll(List<UploadResult> uploaded) {
        uploaded.forEach(result -> imageStorageService.deleteImage(result.fullUrl(), result.thumbnailUrl()));
    }

    private void validateAll(MultipartFile[] files) {
        if (files.length > MAX_IMAGES) {
            throw new ValidationException("You can attach at most 3 photos");
        }
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                throw new ValidationException("Photo is empty");
            }
            if (file.getSize() > MAX_BYTES_PER_IMAGE) {
                throw new ValidationException("Each photo must be smaller than 5MB");
            }
            if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
                throw new ValidationException("Only JPEG, PNG, and WebP photos are allowed");
            }
        }
    }
}
```

No resizing or re-encoding here. `ImageStorageService` already runs every upload through
Thumbnailator and returns the full/thumbnail pair — that is the compression the spec requires.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd backend && ./gradlew test --tests com.fashionrental.review.ReviewImageUploaderTest`
Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/fashionrental/review/ReviewImageUploader.java \
        backend/src/test/java/com/fashionrental/review/ReviewImageUploaderTest.java
git commit -m "feat(review): validate and store review photos under the reviews namespace"
```

---

### Task 5: DTOs, sort enum, and mapper

**Files:**
- Create: `backend/src/main/java/com/fashionrental/review/model/ReviewSort.java`
- Create: `backend/src/main/java/com/fashionrental/review/model/request/SubmitReviewRequest.java`
- Create: `backend/src/main/java/com/fashionrental/review/model/request/UpdateReviewStatusRequest.java`
- Create: `backend/src/main/java/com/fashionrental/review/model/response/ReviewImageResponse.java`
- Create: `backend/src/main/java/com/fashionrental/review/model/response/PublicReviewResponse.java`
- Create: `backend/src/main/java/com/fashionrental/review/model/response/AdminReviewResponse.java`
- Create: `backend/src/main/java/com/fashionrental/review/model/response/SubmitReviewResponse.java`
- Create: `backend/src/main/java/com/fashionrental/review/ReviewMapper.java`
- Test: `backend/src/test/java/com/fashionrental/review/ReviewMapperTest.java`
- Test: `backend/src/test/java/com/fashionrental/review/model/request/SubmitReviewRequestValidationTest.java`

**Interfaces:**
- Consumes: `Review`, `ReviewImage` (Task 1).
- Produces: `ReviewSort { NEWEST, HIGHEST_RATED }`; the four response records; and static
  `ReviewMapper.toPublicResponse(Review) → PublicReviewResponse`,
  `ReviewMapper.toAdminResponse(Review) → AdminReviewResponse`,
  `ReviewMapper.toSubmitResponse(Review) → SubmitReviewResponse`.

- [ ] **Step 1: Write the failing mapper test**

```java
package com.fashionrental.review;

import com.fashionrental.review.model.response.PublicReviewResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewMapperTest {

    @Test
    void should_omit_phone_from_the_public_response_type() {
        String[] components = Arrays.stream(PublicReviewResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toArray(String[]::new);

        assertThat(components).doesNotContain("phone", "submitterIp", "status");
    }

    @Test
    void should_map_every_public_field_from_the_entity() {
        Review review = ReviewTestFixtures.approvedReview();

        PublicReviewResponse response = ReviewMapper.toPublicResponse(review);

        assertThat(response.reviewerName()).isEqualTo("Priya S");
        assertThat(response.itemDescription()).isEqualTo("Red bridal lehenga");
        assertThat(response.rating()).isEqualTo(5);
        assertThat(response.reviewText()).isEqualTo("Beautiful outfit, fit perfectly.");
        assertThat(response.images()).hasSize(1);
        assertThat(response.images().getFirst().thumbnailUrl()).isEqualTo("thumb-1");
    }

    @Test
    void should_include_phone_in_the_admin_response() {
        Review review = ReviewTestFixtures.approvedReview();

        assertThat(ReviewMapper.toAdminResponse(review).phone()).isEqualTo("9876543210");
    }
}
```

`should_omit_phone_from_the_public_response_type` is a reflection assertion — it fails when the
suite runs, not at compile time. That is still the strongest guard available: adding a `phone`
component to the record turns this test red, and the assertion states the invariant in one line
that a reviewer cannot misread.

- [ ] **Step 2: Write the shared test fixture**

`backend/src/test/java/com/fashionrental/review/ReviewTestFixtures.java`:

```java
package com.fashionrental.review;

final class ReviewTestFixtures {

    private ReviewTestFixtures() {
    }

    static Review approvedReview() {
        Review review = new Review();
        review.setReviewerName("Priya S");
        review.setPhone("9876543210");
        review.setItemDescription("Red bridal lehenga");
        review.setRating(5);
        review.setReviewText("Beautiful outfit, fit perfectly.");
        review.setStatus(Review.Status.APPROVED);
        review.setSubmitterIp("203.0.113.7");

        ReviewImage image = new ReviewImage();
        image.setUrl("full-1");
        image.setThumbnailUrl("thumb-1");
        image.setSortOrder(0);
        review.addImage(image);

        return review;
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd backend && ./gradlew test --tests com.fashionrental.review.ReviewMapperTest`
Expected: FAIL — compilation error, `ReviewMapper` and the response records do not exist.

- [ ] **Step 4: Write the sort enum and response records**

```java
package com.fashionrental.review.model;

public enum ReviewSort { NEWEST, HIGHEST_RATED }
```

```java
package com.fashionrental.review.model.response;

import java.util.UUID;

public record ReviewImageResponse(UUID id, String url, String thumbnailUrl) {}
```

```java
package com.fashionrental.review.model.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PublicReviewResponse(
        UUID id,
        String reviewerName,
        String itemDescription,
        int rating,
        String reviewText,
        OffsetDateTime createdAt,
        List<ReviewImageResponse> images
) {}
```

```java
package com.fashionrental.review.model.response;

import com.fashionrental.review.Review;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AdminReviewResponse(
        UUID id,
        String reviewerName,
        String phone,
        String itemDescription,
        int rating,
        String reviewText,
        Review.Status status,
        OffsetDateTime createdAt,
        OffsetDateTime moderatedAt,
        List<ReviewImageResponse> images
) {}
```

```java
package com.fashionrental.review.model.response;

import com.fashionrental.review.Review;

import java.util.UUID;

public record SubmitReviewResponse(UUID id, Review.Status status) {}
```

- [ ] **Step 5: Write the request records**

```java
package com.fashionrental.review.model.request;

import jakarta.validation.constraints.*;

public record SubmitReviewRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 80, message = "Name must be 80 characters or fewer")
        String reviewerName,

        @NotBlank(message = "Mobile number is required")
        @Pattern(regexp = "^[6-9]\\d{9}$", message = "Enter a valid 10-digit Indian mobile number")
        String phone,

        @NotBlank(message = "Tell us what you rented")
        @Size(max = 100, message = "Keep this to 100 characters or fewer")
        String itemDescription,

        @NotNull(message = "Please give a rating")
        @Min(value = 1, message = "Rating must be between 1 and 5")
        @Max(value = 5, message = "Rating must be between 1 and 5")
        Integer rating,

        @NotBlank(message = "Review cannot be empty")
        @Size(max = 256, message = "Review must be 256 characters or fewer")
        String reviewText
) {}
```

```java
package com.fashionrental.review.model.request;

import com.fashionrental.review.Review;
import jakarta.validation.constraints.NotNull;

public record UpdateReviewStatusRequest(@NotNull(message = "Status is required") Review.Status status) {}
```

- [ ] **Step 6: Write the mapper**

```java
package com.fashionrental.review;

import com.fashionrental.review.model.response.AdminReviewResponse;
import com.fashionrental.review.model.response.PublicReviewResponse;
import com.fashionrental.review.model.response.ReviewImageResponse;
import com.fashionrental.review.model.response.SubmitReviewResponse;

import java.util.List;

public final class ReviewMapper {

    private ReviewMapper() {
    }

    public static PublicReviewResponse toPublicResponse(Review review) {
        return new PublicReviewResponse(
                review.getId(),
                review.getReviewerName(),
                review.getItemDescription(),
                review.getRating(),
                review.getReviewText(),
                review.getCreatedAt(),
                toImageResponses(review));
    }

    public static AdminReviewResponse toAdminResponse(Review review) {
        return new AdminReviewResponse(
                review.getId(),
                review.getReviewerName(),
                review.getPhone(),
                review.getItemDescription(),
                review.getRating(),
                review.getReviewText(),
                review.getStatus(),
                review.getCreatedAt(),
                review.getModeratedAt(),
                toImageResponses(review));
    }

    public static SubmitReviewResponse toSubmitResponse(Review review) {
        return new SubmitReviewResponse(review.getId(), review.getStatus());
    }

    private static List<ReviewImageResponse> toImageResponses(Review review) {
        return review.getImages().stream()
                .map(image -> new ReviewImageResponse(image.getId(), image.getUrl(), image.getThumbnailUrl()))
                .toList();
    }
}
```

- [ ] **Step 7: Write the validation test**

```java
package com.fashionrental.review.model.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubmitReviewRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    private static SubmitReviewRequest valid() {
        return new SubmitReviewRequest("Priya S", "9876543210", "Red lehenga", 5, "Lovely outfit.");
    }

    @Test
    void should_accept_a_fully_valid_request() {
        assertThat(validator.validate(valid())).isEmpty();
    }

    @Test
    void should_reject_review_text_longer_than_256_characters() {
        SubmitReviewRequest request = new SubmitReviewRequest(
                "Priya S", "9876543210", "Red lehenga", 5, "x".repeat(257));

        assertThat(validator.validate(request))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("reviewText");
    }

    @Test
    void should_accept_review_text_of_exactly_256_characters() {
        SubmitReviewRequest request = new SubmitReviewRequest(
                "Priya S", "9876543210", "Red lehenga", 5, "x".repeat(256));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void should_reject_rating_below_one_and_above_five() {
        assertThat(validator.validate(
                new SubmitReviewRequest("Priya S", "9876543210", "Red lehenga", 0, "Fine."))).isNotEmpty();
        assertThat(validator.validate(
                new SubmitReviewRequest("Priya S", "9876543210", "Red lehenga", 6, "Fine."))).isNotEmpty();
    }

    @Test
    void should_reject_a_phone_that_is_not_a_ten_digit_indian_mobile_number() {
        assertThat(validator.validate(
                new SubmitReviewRequest("Priya S", "1234567890", "Red lehenga", 5, "Fine."))).isNotEmpty();
        assertThat(validator.validate(
                new SubmitReviewRequest("Priya S", "98765", "Red lehenga", 5, "Fine."))).isNotEmpty();
    }

    @Test
    void should_reject_a_blank_reviewer_name() {
        assertThat(validator.validate(
                new SubmitReviewRequest("   ", "9876543210", "Red lehenga", 5, "Fine."))).isNotEmpty();
    }
}
```

`should_accept_review_text_of_exactly_256_characters` pins the boundary — 256 is allowed, 257 is
not. A `@Size(max = 255)` typo passes the "too long" test and fails this one.

- [ ] **Step 8: Run both tests to verify they pass**

Run: `cd backend && ./gradlew test --tests 'com.fashionrental.review.ReviewMapperTest' --tests 'com.fashionrental.review.model.request.SubmitReviewRequestValidationTest'`
Expected: PASS, 9 tests.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/fashionrental/review/model/ \
        backend/src/main/java/com/fashionrental/review/ReviewMapper.java \
        backend/src/test/java/com/fashionrental/review/
git commit -m "feat(review): add review DTOs and mapper with phone excluded from public shape"
```

---

### Task 6: ReviewService

**Files:**
- Create: `backend/src/main/java/com/fashionrental/review/ReviewService.java`
- Test: `backend/src/test/java/com/fashionrental/review/ReviewServiceTest.java`

**Interfaces:**
- Consumes: `ReviewRepository`, `ReviewSubmissionGuard`, `ReviewImageUploader`, `ImageStorageService`,
  `ReviewMapper`, `ReviewSort`, all request/response records.
- Produces:
  `submit(SubmitReviewRequest, MultipartFile[], String submitterIp) → SubmitReviewResponse`;
  `listPublic(ReviewSort, int page) → Page<PublicReviewResponse>`;
  `listForModeration(Review.Status statusOrNull, int page, int size) → Page<AdminReviewResponse>`;
  `updateStatus(UUID, Review.Status) → AdminReviewResponse`;
  `delete(UUID) → void`.

- [ ] **Step 1: Write the failing test**

```java
package com.fashionrental.review;

import com.fashionrental.common.exception.ResourceNotFoundException;
import com.fashionrental.inventory.storage.ImageStorageService;
import com.fashionrental.inventory.storage.UploadResult;
import com.fashionrental.review.model.ReviewSort;
import com.fashionrental.review.model.request.SubmitReviewRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    private static final String IP = "203.0.113.7";

    @Mock private ReviewRepository reviewRepository;
    @Mock private ReviewSubmissionGuard reviewSubmissionGuard;
    @Mock private ReviewImageUploader reviewImageUploader;
    @Mock private ImageStorageService imageStorageService;

    @InjectMocks private ReviewService reviewService;

    private static SubmitReviewRequest request() {
        return new SubmitReviewRequest("Priya S", "9876543210", "Red lehenga", 5, "Lovely outfit.");
    }

    @Test
    void should_persist_a_new_review_with_pending_status() {
        when(reviewImageUploader.uploadAll(any())).thenReturn(List.of());
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> i.getArgument(0));

        reviewService.submit(request(), null, IP);

        ArgumentCaptor<Review> saved = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(Review.Status.PENDING);
        assertThat(saved.getValue().getSubmitterIp()).isEqualTo(IP);
        assertThat(saved.getValue().getPhone()).isEqualTo("9876543210");
    }

    @Test
    void should_check_the_rate_limit_before_uploading_anything() {
        doThrow(new RuntimeException("limited")).when(reviewSubmissionGuard)
                .checkSubmissionAllowed(IP, "9876543210");

        assertThatThrownBy(() -> reviewService.submit(request(), null, IP));

        verify(reviewImageUploader, never()).uploadAll(any());
    }

    @Test
    void should_attach_uploaded_images_in_submission_order() {
        when(reviewImageUploader.uploadAll(any()))
                .thenReturn(List.of(new UploadResult("f1", "t1"), new UploadResult("f2", "t2")));
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> i.getArgument(0));

        reviewService.submit(request(), null, IP);

        ArgumentCaptor<Review> saved = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(saved.capture());
        assertThat(saved.getValue().getImages())
                .extracting(ReviewImage::getUrl, ReviewImage::getSortOrder)
                .containsExactly(tuple("f1", 0), tuple("f2", 1));
    }

    @Test
    void should_delete_uploaded_images_when_persisting_the_review_fails() {
        List<UploadResult> uploaded = List.of(new UploadResult("f1", "t1"));
        when(reviewImageUploader.uploadAll(any())).thenReturn(uploaded);
        when(reviewRepository.save(any(Review.class))).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> reviewService.submit(request(), null, IP));

        verify(reviewImageUploader).deleteAll(uploaded);
    }

    @Test
    void should_request_ten_approved_reviews_sorted_by_newest() {
        when(reviewRepository.findByStatus(eq(Review.Status.APPROVED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        reviewService.listPublic(ReviewSort.NEWEST, 0);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(reviewRepository).findByStatus(eq(Review.Status.APPROVED), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Test
    void should_break_rating_ties_with_created_at_when_sorting_by_highest_rated() {
        when(reviewRepository.findByStatus(eq(Review.Status.APPROVED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        reviewService.listPublic(ReviewSort.HIGHEST_RATED, 0);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(reviewRepository).findByStatus(eq(Review.Status.APPROVED), pageable.capture());
        assertThat(pageable.getValue().getSort()).isEqualTo(
                Sort.by(Sort.Direction.DESC, "rating").and(Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @Test
    void should_stamp_moderated_at_when_status_changes() {
        Review review = ReviewTestFixtures.approvedReview();
        UUID id = UUID.randomUUID();
        when(reviewRepository.findById(id)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> i.getArgument(0));

        reviewService.updateStatus(id, Review.Status.REJECTED);

        assertThat(review.getStatus()).isEqualTo(Review.Status.REJECTED);
        assertThat(review.getModeratedAt()).isNotNull();
    }

    @Test
    void should_throw_when_moderating_a_review_that_does_not_exist() {
        UUID id = UUID.randomUUID();
        when(reviewRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.updateStatus(id, Review.Status.APPROVED))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void should_delete_stored_objects_before_deleting_the_review() {
        Review review = ReviewTestFixtures.approvedReview();
        UUID id = UUID.randomUUID();
        when(reviewRepository.findById(id)).thenReturn(Optional.of(review));

        reviewService.delete(id);

        InOrder inOrder = inOrder(imageStorageService, reviewRepository);
        inOrder.verify(imageStorageService).deleteImage("full-1", "thumb-1");
        inOrder.verify(reviewRepository).delete(review);
    }
}
```

Add `import static org.assertj.core.api.Assertions.tuple;` and
`import org.mockito.InOrder;` alongside the imports above.

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && ./gradlew test --tests com.fashionrental.review.ReviewServiceTest`
Expected: FAIL — compilation error, `ReviewService` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.fashionrental.review;

import com.fashionrental.common.exception.ResourceNotFoundException;
import com.fashionrental.inventory.storage.ImageStorageService;
import com.fashionrental.inventory.storage.UploadResult;
import com.fashionrental.review.model.ReviewSort;
import com.fashionrental.review.model.request.SubmitReviewRequest;
import com.fashionrental.review.model.response.AdminReviewResponse;
import com.fashionrental.review.model.response.PublicReviewResponse;
import com.fashionrental.review.model.response.SubmitReviewResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ReviewService {

    private static final int PUBLIC_PAGE_SIZE = 10;

    private final ReviewRepository reviewRepository;
    private final ReviewSubmissionGuard reviewSubmissionGuard;
    private final ReviewImageUploader reviewImageUploader;
    private final ImageStorageService imageStorageService;

    public ReviewService(ReviewRepository reviewRepository,
                         ReviewSubmissionGuard reviewSubmissionGuard,
                         ReviewImageUploader reviewImageUploader,
                         ImageStorageService imageStorageService) {
        this.reviewRepository = reviewRepository;
        this.reviewSubmissionGuard = reviewSubmissionGuard;
        this.reviewImageUploader = reviewImageUploader;
        this.imageStorageService = imageStorageService;
    }

    public SubmitReviewResponse submit(SubmitReviewRequest request, MultipartFile[] images, String submitterIp) {
        reviewSubmissionGuard.checkSubmissionAllowed(submitterIp, request.phone());

        List<UploadResult> uploaded = reviewImageUploader.uploadAll(images);
        try {
            return ReviewMapper.toSubmitResponse(reviewRepository.save(buildPending(request, submitterIp, uploaded)));
        } catch (RuntimeException e) {
            reviewImageUploader.deleteAll(uploaded);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Page<PublicReviewResponse> listPublic(ReviewSort sort, int page) {
        Pageable pageable = PageRequest.of(page, PUBLIC_PAGE_SIZE, sortFor(sort));
        return reviewRepository.findByStatus(Review.Status.APPROVED, pageable)
                .map(ReviewMapper::toPublicResponse);
    }

    @Transactional(readOnly = true)
    public Page<AdminReviewResponse> listForModeration(Review.Status status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Review> reviews = status == null
                ? reviewRepository.findAll(pageable)
                : reviewRepository.findByStatus(status, pageable);
        return reviews.map(ReviewMapper::toAdminResponse);
    }

    public AdminReviewResponse updateStatus(UUID id, Review.Status status) {
        Review review = findOrThrow(id);
        review.setStatus(status);
        review.setModeratedAt(OffsetDateTime.now());
        return ReviewMapper.toAdminResponse(reviewRepository.save(review));
    }

    public void delete(UUID id) {
        Review review = findOrThrow(id);
        review.getImages().forEach(image -> imageStorageService.deleteImage(image.getUrl(), image.getThumbnailUrl()));
        reviewRepository.delete(review);
    }

    private Review findOrThrow(UUID id) {
        return reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found: " + id));
    }

    private Review buildPending(SubmitReviewRequest request, String submitterIp, List<UploadResult> uploaded) {
        Review review = new Review();
        review.setReviewerName(request.reviewerName());
        review.setPhone(request.phone());
        review.setItemDescription(request.itemDescription());
        review.setRating(request.rating());
        review.setReviewText(request.reviewText());
        review.setStatus(Review.Status.PENDING);
        review.setSubmitterIp(submitterIp);

        for (int i = 0; i < uploaded.size(); i++) {
            ReviewImage image = new ReviewImage();
            image.setUrl(uploaded.get(i).fullUrl());
            image.setThumbnailUrl(uploaded.get(i).thumbnailUrl());
            image.setSortOrder(i);
            review.addImage(image);
        }
        return review;
    }

    private static Sort sortFor(ReviewSort sort) {
        return sort == ReviewSort.HIGHEST_RATED
                ? Sort.by(Sort.Direction.DESC, "rating").and(Sort.by(Sort.Direction.DESC, "createdAt"))
                : Sort.by(Sort.Direction.DESC, "createdAt");
    }
}
```

`listPublic` takes neither a page size nor a status, so no caller can widen either.

`delete` removes the stored objects before the row. If object deletion throws, the transaction
rolls back and the review survives — a stale row beats an orphaned object whose URL is no longer
recorded anywhere.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd backend && ./gradlew test --tests com.fashionrental.review.ReviewServiceTest`
Expected: PASS, 9 tests.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/fashionrental/review/ReviewService.java \
        backend/src/test/java/com/fashionrental/review/ReviewServiceTest.java
git commit -m "feat(review): orchestrate submission, public listing, and moderation"
```

---

### Task 7: Controllers and security rule

**Files:**
- Create: `backend/src/main/java/com/fashionrental/review/ReviewPublicController.java`
- Create: `backend/src/main/java/com/fashionrental/review/ReviewAdminController.java`
- Modify: `backend/src/main/java/com/fashionrental/config/SecurityConfig.java`
- Test: `backend/src/test/java/com/fashionrental/review/ReviewPublicControllerTest.java`
- Test: `backend/src/test/java/com/fashionrental/review/ReviewAdminControllerTest.java`

**Interfaces:**
- Consumes: `ReviewService` (Task 6), `ClientIpResolver` (Task 2).
- Produces: the HTTP surface. No Java consumers downstream.

- [ ] **Step 1: Write the failing controller tests**

```java
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
import static org.mockito.ArgumentMatchers.eq;
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
```

```java
package com.fashionrental.review;

import com.fashionrental.config.JwtConfig;
import com.fashionrental.config.SecurityConfig;
import com.fashionrental.config.SecurityErrorHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
    @WithMockUser(roles = "EXECUTIVE")
    void should_return_403_for_executive_role_on_the_moderation_queue() throws Exception {
        mockMvc.perform(get("/api/reviews")).andExpect(status().isForbidden());
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
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd backend && ./gradlew test --tests 'com.fashionrental.review.Review*ControllerTest'`
Expected: FAIL — compilation error, the controllers do not exist.

- [ ] **Step 3: Write the public controller**

```java
package com.fashionrental.review;

import com.fashionrental.common.response.ApiResponse;
import com.fashionrental.common.util.ClientIpResolver;
import com.fashionrental.review.model.ReviewSort;
import com.fashionrental.review.model.request.SubmitReviewRequest;
import com.fashionrental.review.model.response.PublicReviewResponse;
import com.fashionrental.review.model.response.SubmitReviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Reviews", description = "Public review submission and browsing")
@RestController
@RequestMapping("/api/public/reviews")
public class ReviewPublicController {

    private final ReviewService reviewService;

    public ReviewPublicController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Operation(summary = "Submit a review with up to three photos; lands as PENDING")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<SubmitReviewResponse>> submitReview(
            @Valid @RequestPart("review") SubmitReviewRequest request,
            @RequestPart(value = "images", required = false) MultipartFile[] images,
            HttpServletRequest httpRequest
    ) {
        String submitterIp = ClientIpResolver.resolve(httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(reviewService.submit(request, images, submitterIp)));
    }

    @Operation(summary = "List approved reviews, ten per page")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<PublicReviewResponse>>> listReviews(
            @RequestParam(defaultValue = "NEWEST") ReviewSort sort,
            @RequestParam(defaultValue = "0") int page
    ) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.listPublic(sort, page)));
    }
}
```

- [ ] **Step 4: Write the admin controller**

```java
package com.fashionrental.review;

import com.fashionrental.common.response.ApiResponse;
import com.fashionrental.review.model.request.UpdateReviewStatusRequest;
import com.fashionrental.review.model.response.AdminReviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Review moderation", description = "Owner-only review moderation")
@RestController
@RequestMapping("/api/reviews")
public class ReviewAdminController {

    private final ReviewService reviewService;

    public ReviewAdminController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Operation(summary = "List reviews for moderation, optionally filtered by status")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AdminReviewResponse>>> listReviews(
            @RequestParam(required = false) Review.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.listForModeration(status, page, size)));
    }

    @Operation(summary = "Approve or reject a review")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<AdminReviewResponse>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateReviewStatusRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.updateStatus(id, request.status())));
    }

    @Operation(summary = "Delete a review and its stored photos")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteReview(@PathVariable UUID id) {
        reviewService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
```

- [ ] **Step 5: Add the security rule**

In `SecurityConfig.filterChain`, add this line immediately after the existing
`.requestMatchers("/api/gallery", "/api/gallery/**").hasRole("OWNER")` line — it must come
**before** `.requestMatchers("/api/**").authenticated()`:

```java
                        .requestMatchers("/api/reviews", "/api/reviews/**").hasRole("OWNER")
```

`/api/public/**` is already `permitAll()`, so the public routes need no new rule.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd backend && ./gradlew test --tests 'com.fashionrental.review.Review*ControllerTest'`
Expected: PASS, 11 tests.

- [ ] **Step 7: Run the full backend suite**

Run: `cd backend && ./gradlew test`
Expected: PASS. Every pre-existing test must still be green — the `SecurityConfig` edit is the
one change in this task that can break unrelated tests.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/fashionrental/review/ \
        backend/src/main/java/com/fashionrental/config/SecurityConfig.java \
        backend/src/test/java/com/fashionrental/review/
git commit -m "feat(review): expose public submit/list and owner-only moderation endpoints"
```

---

### Task 8: Integration tests

**Files:**
- Create: `backend/src/integrationTest/java/com/fashionrental/review/ReviewRepositoryIT.java`

**Interfaces:**
- Consumes: everything from Tasks 1–7.
- Produces: nothing consumed by later tasks.

- [ ] **Step 1: Write the integration test**

```java
package com.fashionrental.review;

import com.fashionrental.AbstractIntegrationTest;
import com.fashionrental.review.model.ReviewSort;
import com.fashionrental.review.model.response.PublicReviewResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewRepositoryIT extends AbstractIntegrationTest {

    @Autowired private ReviewRepository reviewRepository;
    @Autowired private ReviewService reviewService;

    @BeforeEach
    void clearReviews() {
        reviewRepository.deleteAll();
    }

    private Review persist(Review.Status status, int rating, String name) {
        Review review = new Review();
        review.setReviewerName(name);
        review.setPhone("9876543210");
        review.setItemDescription("Red lehenga");
        review.setRating(rating);
        review.setReviewText("Lovely outfit.");
        review.setStatus(status);
        review.setSubmitterIp("203.0.113.7");
        return reviewRepository.save(review);
    }

    @Test
    void should_return_only_approved_reviews_on_the_public_query() {
        persist(Review.Status.APPROVED, 5, "Approved");
        persist(Review.Status.PENDING, 5, "Pending");
        persist(Review.Status.REJECTED, 5, "Rejected");

        Page<PublicReviewResponse> page = reviewService.listPublic(ReviewSort.NEWEST, 0);

        assertThat(page.getContent()).extracting(PublicReviewResponse::reviewerName)
                .containsExactly("Approved");
    }

    @Test
    void should_return_ten_reviews_per_page() {
        for (int i = 0; i < 25; i++) {
            persist(Review.Status.APPROVED, 5, "Reviewer " + i);
        }

        assertThat(reviewService.listPublic(ReviewSort.NEWEST, 0).getTotalElements()).isEqualTo(25);
        assertThat(reviewService.listPublic(ReviewSort.NEWEST, 0).getTotalPages()).isEqualTo(3);
        assertThat(reviewService.listPublic(ReviewSort.NEWEST, 0).getContent()).hasSize(10);
        assertThat(reviewService.listPublic(ReviewSort.NEWEST, 1).getContent()).hasSize(10);
        assertThat(reviewService.listPublic(ReviewSort.NEWEST, 2).getContent()).hasSize(5);
    }

    @Test
    void should_never_repeat_or_drop_a_review_across_pages_when_ratings_tie() {
        for (int i = 0; i < 25; i++) {
            persist(Review.Status.APPROVED, 4, "Reviewer " + i);
        }

        List<UUID> seen = List.of(0, 1, 2).stream()
                .flatMap(page -> reviewService.listPublic(ReviewSort.HIGHEST_RATED, page)
                        .getContent().stream())
                .map(PublicReviewResponse::id)
                .toList();

        assertThat(seen).hasSize(25);
        assertThat(seen).doesNotHaveDuplicates();
    }

    @Test
    void should_order_highest_rated_first() {
        persist(Review.Status.APPROVED, 2, "Two star");
        persist(Review.Status.APPROVED, 5, "Five star");
        persist(Review.Status.APPROVED, 3, "Three star");

        assertThat(reviewService.listPublic(ReviewSort.HIGHEST_RATED, 0).getContent())
                .extracting(PublicReviewResponse::rating)
                .containsExactly(5, 3, 2);
    }

    @Test
    void should_count_only_reviews_inside_the_rate_limit_window() {
        persist(Review.Status.PENDING, 5, "Recent");

        assertThat(reviewRepository.countBySubmitterIpAndCreatedAtAfter(
                "203.0.113.7", OffsetDateTime.now().minusHours(1))).isEqualTo(1);
        assertThat(reviewRepository.countBySubmitterIpAndCreatedAtAfter(
                "203.0.113.7", OffsetDateTime.now().plusMinutes(1))).isZero();
    }

    @Test
    void should_cascade_delete_review_images_when_the_review_is_deleted() {
        Review review = new Review();
        review.setReviewerName("Priya S");
        review.setPhone("9876543210");
        review.setItemDescription("Red lehenga");
        review.setRating(5);
        review.setReviewText("Lovely outfit.");
        review.setSubmitterIp("203.0.113.7");
        ReviewImage image = new ReviewImage();
        image.setUrl("full-1");
        image.setThumbnailUrl("thumb-1");
        image.setSortOrder(0);
        review.addImage(image);
        Review saved = reviewRepository.save(review);

        reviewRepository.deleteById(saved.getId());

        assertThat(reviewRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void should_reject_a_rating_outside_one_to_five_at_the_database_level() {
        Review review = new Review();
        review.setReviewerName("Priya S");
        review.setPhone("9876543210");
        review.setItemDescription("Red lehenga");
        review.setRating(9);
        review.setReviewText("Lovely outfit.");
        review.setSubmitterIp("203.0.113.7");

        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> reviewRepository.saveAndFlush(review))).isNotNull();
    }
}
```

`should_never_repeat_or_drop_a_review_across_pages_when_ratings_tie` is the test that justifies
the `createdAt` tiebreaker. Remove the tiebreaker from `ReviewService.sortFor` and this test goes
red — without it, Postgres is free to return tied rows in any order per query, so a review can
appear on two pages and another can vanish.

- [ ] **Step 2: Run the integration test**

Run: `cd backend && ./gradlew integrationTest --tests com.fashionrental.review.ReviewRepositoryIT`
Expected: PASS, 7 tests.

On a local machine using podman rather than Docker, export these first:
```bash
export DOCKER_HOST="unix://$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}')"
export TESTCONTAINERS_RYUK_DISABLED=true
```

- [ ] **Step 3: Run the whole integration suite**

Run: `cd backend && ./gradlew integrationTest`
Expected: PASS, no regressions.

- [ ] **Step 4: Commit**

```bash
git add backend/src/integrationTest/java/com/fashionrental/review/
git commit -m "test(review): cover approval filtering, pagination stability, and cascade delete"
```

---

### Task 9: Frontend types and API client

**Files:**
- Modify: `frontend/src/types/api.ts`
- Modify: `frontend/src/types/inventory.ts`
- Modify: `frontend/src/api/items.ts`
- Create: `frontend/src/types/review.ts`
- Create: `frontend/src/api/reviews.ts`
- Modify: `frontend/src/test/handlers.ts`

**Interfaces:**
- Consumes: the HTTP contract from Task 7.
- Produces: `PageResult<T>` from `types/api`; the review types; and `reviewsApi` with
  `submit(data: SubmitReviewRequest, images: File[]) → Promise<SubmitReviewResult>`,
  `listPublic({ sort, page }) → Promise<PageResult<PublicReview>>`,
  `listForModeration({ status?, page, size }) → Promise<PageResult<AdminReview>>`,
  `updateStatus(id, status) → Promise<AdminReview>`,
  `remove(id) → Promise<void>`.

- [ ] **Step 1: Move `PageResult` to `types/api.ts`**

Cut this block from `frontend/src/types/inventory.ts` and paste it at the end of
`frontend/src/types/api.ts`:

```ts
export interface PageResult<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}
```

In `frontend/src/api/items.ts`, remove `PageResult` from the `../types/inventory` import list and
add a new import:

```ts
import type { ApiResponse, PageResult } from '../types/api'
```

(Keep whatever else that file already imports from `../types/api`; merge, do not duplicate the
import statement.)

- [ ] **Step 2: Verify the move compiles before adding anything new**

Run: `cd frontend && pnpm type-check`
Expected: PASS, no errors. Only two files referenced `PageResult`, so anything else is a mistake
in this step.

- [ ] **Step 3: Write `types/review.ts`**

```ts
export type ReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
export type ReviewSort = 'NEWEST' | 'HIGHEST_RATED'

export interface ReviewImage {
  id: string
  url: string
  thumbnailUrl: string
}

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

export interface SubmitReviewResult {
  id: string
  status: ReviewStatus
}
```

- [ ] **Step 4: Write `api/reviews.ts`**

```ts
import { client } from './client'
import { publicClient } from './public'
import type { ApiResponse, PageResult } from '../types/api'
import type {
  AdminReview,
  PublicReview,
  ReviewSort,
  ReviewStatus,
  SubmitReviewRequest,
  SubmitReviewResult,
} from '../types/review'

export const reviewsApi = {
  submit: (data: SubmitReviewRequest, images: File[]): Promise<SubmitReviewResult> => {
    const form = new FormData()
    form.append('review', new Blob([JSON.stringify(data)], { type: 'application/json' }))
    images.forEach(file => form.append('images', file))
    // Content-Type must be unset so the browser supplies the multipart boundary;
    // publicClient defaults it to application/json.
    return publicClient
      .post<ApiResponse<SubmitReviewResult>>('/reviews', form, {
        headers: { 'Content-Type': undefined },
      })
      .then(r => r.data.data!)
  },

  listPublic: (params: { sort: ReviewSort; page: number }): Promise<PageResult<PublicReview>> =>
    publicClient
      .get<ApiResponse<PageResult<PublicReview>>>('/reviews', { params })
      .then(r => r.data.data!),

  listForModeration: (params: { status?: ReviewStatus; page: number; size: number }): Promise<PageResult<AdminReview>> =>
    client
      .get<ApiResponse<PageResult<AdminReview>>>('/reviews', { params })
      .then(r => r.data.data!),

  updateStatus: (id: string, status: ReviewStatus): Promise<AdminReview> =>
    client
      .patch<ApiResponse<AdminReview>>(`/reviews/${id}/status`, { status })
      .then(r => r.data.data!),

  remove: (id: string): Promise<void> =>
    client.delete(`/reviews/${id}`).then(() => undefined),
}
```

Check how `client` is exported from `frontend/src/api/client.ts` (default vs named) and match it —
the other `src/api/*.ts` files show the correct form.

- [ ] **Step 5: Add default msw handlers**

Append to `frontend/src/test/handlers.ts`, following the shape of the handlers already there:

```ts
const publicReview = (id: string, name: string, rating: number) => ({
  id,
  reviewerName: name,
  itemDescription: 'Red bridal lehenga',
  rating,
  reviewText: 'Beautiful outfit, fit perfectly.',
  createdAt: '2026-09-20T14:30:00+05:30',
  images: [],
})

export const reviewHandlers = [
  http.get('*/api/public/reviews', () =>
    HttpResponse.json({
      success: true,
      data: {
        content: [publicReview('review-1', 'Priya S', 5), publicReview('review-2', 'Anil K', 4)],
        totalElements: 2,
        totalPages: 1,
        number: 0,
        size: 10,
      },
      error: null,
    })),

  http.post('*/api/public/reviews', () =>
    HttpResponse.json({ success: true, data: { id: 'review-3', status: 'PENDING' }, error: null }, { status: 201 })),
]
```

Register `...reviewHandlers` in the array the file exports as the default handler set.

- [ ] **Step 6: Verify types and the existing suite**

Run: `cd frontend && pnpm type-check && pnpm test`
Expected: PASS, no regressions.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/types/ frontend/src/api/ frontend/src/test/handlers.ts
git commit -m "feat(review): add review types and API client; move PageResult to types/api"
```

---

### Task 10: Public reviews page

**Files:**
- Create: `frontend/src/pages/public/ReviewsPage.tsx`
- Test: `frontend/src/pages/public/ReviewsPage.test.tsx`
- Modify: `frontend/src/App.tsx`

**Interfaces:**
- Consumes: `reviewsApi.listPublic` (Task 9).
- Produces: default-exported `ReviewsPage` component taking no props.

Build the read path before the write path — it is verifiable against seeded data on its own.

- [ ] **Step 1: Write the failing test**

```tsx
import { describe, it, expect } from 'vitest'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { renderWithProviders, waitFor } from '../../test/render'
import { server } from '../../test/server'
import ReviewsPage from './ReviewsPage'

// Seeded by the default msw handler (src/test/handlers.ts):
//   review-1 — Priya S, 5 stars
//   review-2 — Anil K,  4 stars

describe('ReviewsPage', () => {
  it('renders every review returned for the first page', async () => {
    const { findByText } = renderWithProviders(<ReviewsPage />, { route: '/reviews' })

    expect(await findByText('Priya S')).toBeInTheDocument()
    expect(await findByText('Anil K')).toBeInTheDocument()
  })

  it('never renders a phone number', async () => {
    const { findByText, container } = renderWithProviders(<ReviewsPage />, { route: '/reviews' })

    await findByText('Priya S')

    // The public API must not even return a phone. Assert on rendered text so a future
    // change that starts echoing one is caught here and not in production.
    expect(container.textContent).not.toMatch(/\d{10}/)
  })

  it('requests HIGHEST_RATED when the sort is changed', async () => {
    const requested: string[] = []
    server.use(
      http.get('*/api/public/reviews', ({ request }) => {
        requested.push(new URL(request.url).searchParams.get('sort') ?? 'MISSING')
        return HttpResponse.json({
          success: true,
          data: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 },
          error: null,
        })
      }),
    )
    const user = userEvent.setup()
    const { getByRole, findByTitle } = renderWithProviders(<ReviewsPage />, { route: '/reviews' })

    await waitFor(() => expect(requested).toEqual(['NEWEST']))

    await user.click(getByRole('combobox'))
    await user.click(await findByTitle('Highest rated'))

    await waitFor(() => expect(requested).toContain('HIGHEST_RATED'))
  })

  it('requests the next page when pagination is used', async () => {
    const requested: string[] = []
    server.use(
      http.get('*/api/public/reviews', ({ request }) => {
        requested.push(new URL(request.url).searchParams.get('page') ?? 'MISSING')
        return HttpResponse.json({
          success: true,
          data: {
            content: [{
              id: 'r', reviewerName: 'Someone', itemDescription: 'Dress', rating: 5,
              reviewText: 'Great.', createdAt: '2026-09-20T14:30:00+05:30', images: [],
            }],
            totalElements: 25, totalPages: 3, number: 0, size: 10,
          },
          error: null,
        })
      }),
    )
    const user = userEvent.setup()
    const { findByText, getByTitle } = renderWithProviders(<ReviewsPage />, { route: '/reviews' })

    await findByText('Someone')
    await user.click(getByTitle('2'))

    await waitFor(() => expect(requested).toContain('1'))
  })

  it('shows an empty state when nothing is approved yet', async () => {
    server.use(
      http.get('*/api/public/reviews', () =>
        HttpResponse.json({
          success: true,
          data: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 },
          error: null,
        })),
    )
    const { findByText } = renderWithProviders(<ReviewsPage />, { route: '/reviews' })

    expect(await findByText(/no reviews yet/i)).toBeInTheDocument()
  })
})
```

Pagination is zero-based on the server and one-based in AntD's `Pagination`. The
"requests the next page" test asserts the server receives `1` when the user clicks page **2** —
an off-by-one in that conversion fails here.

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd frontend && pnpm test -- ReviewsPage`
Expected: FAIL — cannot resolve `./ReviewsPage`.

- [ ] **Step 3: Write the component**

```tsx
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Card, Empty, Image, Pagination, Rate, Select, Space, Typography } from 'antd'
import { reviewsApi } from '../../api/reviews'
import PageHeader from '../../components/common/PageHeader'
import LoadingSpinner from '../../components/common/LoadingSpinner'
import type { ReviewSort } from '../../types/review'

const { Text, Paragraph } = Typography

const SORT_OPTIONS: { value: ReviewSort; label: string }[] = [
  { value: 'NEWEST', label: 'Newest' },
  { value: 'HIGHEST_RATED', label: 'Highest rated' },
]

function formatReviewDate(iso: string): string {
  return new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
}

export default function ReviewsPage() {
  const [sort, setSort] = useState<ReviewSort>('NEWEST')
  const [page, setPage] = useState(0)

  const { data, isLoading } = useQuery({
    queryKey: ['reviews', sort, page],
    queryFn: () => reviewsApi.listPublic({ sort, page }),
  })

  function changeSort(next: ReviewSort) {
    setSort(next)
    setPage(0)
  }

  return (
    <div>
      <PageHeader title="Customer Reviews" />

      <Space style={{ marginBottom: 16 }}>
        <Text type="secondary">Sort by</Text>
        <Select<ReviewSort>
          value={sort}
          onChange={changeSort}
          options={SORT_OPTIONS}
          style={{ width: 180 }}
        />
      </Space>

      {isLoading && <LoadingSpinner />}

      {!isLoading && data?.totalElements === 0 && (
        <Empty description="No reviews yet — be the first to leave one." />
      )}

      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        {data?.content.map(review => (
          <Card key={review.id}>
            <Space direction="vertical" size={4} style={{ width: '100%' }}>
              <Space wrap>
                <Text strong>{review.reviewerName}</Text>
                <Rate disabled value={review.rating} />
                <Text type="secondary">{formatReviewDate(review.createdAt)}</Text>
              </Space>
              <Text type="secondary">Rented: {review.itemDescription}</Text>
              <Paragraph style={{ marginBottom: 0 }}>{review.reviewText}</Paragraph>
              {review.images.length > 0 && (
                <Image.PreviewGroup>
                  <Space wrap>
                    {review.images.map(image => (
                      <Image
                        key={image.id}
                        src={image.thumbnailUrl}
                        preview={{ src: image.url }}
                        width={88}
                        height={88}
                        style={{ objectFit: 'cover', borderRadius: 8 }}
                      />
                    ))}
                  </Space>
                </Image.PreviewGroup>
              )}
            </Space>
          </Card>
        ))}
      </Space>

      {(data?.totalPages ?? 0) > 1 && (
        <Pagination
          style={{ marginTop: 24, textAlign: 'center' }}
          current={page + 1}
          total={data?.totalElements ?? 0}
          pageSize={10}
          showSizeChanger={false}
          onChange={next => setPage(next - 1)}
        />
      )}
    </div>
  )
}
```

Check the actual prop shapes of `PageHeader` and `LoadingSpinner` in
`frontend/src/components/common/` and match them; adjust the two usages above if they differ.

- [ ] **Step 4: Add the route**

In `frontend/src/App.tsx`, import the page and add this line directly after the existing
`/gallery` route:

```tsx
<Route path="/reviews" element={<PublicLayout><ReviewsPage /></PublicLayout>} />
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd frontend && pnpm test -- ReviewsPage`
Expected: PASS, 5 tests.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/public/ReviewsPage.tsx \
        frontend/src/pages/public/ReviewsPage.test.tsx \
        frontend/src/App.tsx
git commit -m "feat(review): public reviews page with sort and pagination"
```

---

### Task 11: Submit review page

**Files:**
- Create: `frontend/src/pages/public/SubmitReviewPage.tsx`
- Test: `frontend/src/pages/public/SubmitReviewPage.test.tsx`
- Modify: `frontend/src/App.tsx`

**Interfaces:**
- Consumes: `reviewsApi.submit` (Task 9).
- Produces: default-exported `SubmitReviewPage` component taking no props.

- [ ] **Step 1: Write the failing test**

```tsx
import { describe, it, expect } from 'vitest'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { renderWithProviders, waitFor } from '../../test/render'
import { server } from '../../test/server'
import SubmitReviewPage from './SubmitReviewPage'

type Screen = ReturnType<typeof renderWithProviders>

async function fillValidForm(user: ReturnType<typeof userEvent.setup>, screen: Screen) {
  await user.type(screen.getByLabelText('Your name'), 'Priya S')
  await user.type(screen.getByLabelText('Mobile number'), '9876543210')
  await user.type(screen.getByLabelText('What did you rent?'), 'Red lehenga')
  await user.click(screen.getAllByRole('radio')[4])
  await user.type(screen.getByLabelText('Your review'), 'Beautiful outfit.')
}

describe('SubmitReviewPage', () => {
  it('stops accepting review text at 256 characters', async () => {
    const user = userEvent.setup()
    const screen = renderWithProviders(<SubmitReviewPage />, { route: '/review' })

    const textarea = screen.getByLabelText('Your review') as HTMLTextAreaElement
    await user.click(textarea)
    await user.paste('x'.repeat(300))

    expect(textarea.value).toHaveLength(256)
  })

  it('blocks submission when the mobile number is not ten digits', async () => {
    const user = userEvent.setup()
    const screen = renderWithProviders(<SubmitReviewPage />, { route: '/review' })

    await user.type(screen.getByLabelText('Your name'), 'Priya S')
    await user.type(screen.getByLabelText('Mobile number'), '12345')
    await user.type(screen.getByLabelText('What did you rent?'), 'Red lehenga')
    await user.click(screen.getAllByRole('radio')[4])
    await user.type(screen.getByLabelText('Your review'), 'Beautiful outfit.')
    await user.click(screen.getByRole('button', { name: /submit review/i }))

    expect(await screen.findByText(/valid 10-digit indian mobile number/i)).toBeInTheDocument()
  })

  it('blocks submission when no rating is selected', async () => {
    const user = userEvent.setup()
    const screen = renderWithProviders(<SubmitReviewPage />, { route: '/review' })

    await user.type(screen.getByLabelText('Your name'), 'Priya S')
    await user.type(screen.getByLabelText('Mobile number'), '9876543210')
    await user.type(screen.getByLabelText('What did you rent?'), 'Red lehenga')
    await user.type(screen.getByLabelText('Your review'), 'Beautiful outfit.')
    await user.click(screen.getByRole('button', { name: /submit review/i }))

    expect(await screen.findByText(/please give a rating/i)).toBeInTheDocument()
  })

  it('shows the awaiting-approval confirmation after a successful submit', async () => {
    const user = userEvent.setup()
    const screen = renderWithProviders(<SubmitReviewPage />, { route: '/review' })

    await fillValidForm(user, screen)
    await user.click(screen.getByRole('button', { name: /submit review/i }))

    expect(await screen.findByText(/once it has been approved/i)).toBeInTheDocument()
  })

  it('surfaces the server message when the API responds 429', async () => {
    server.use(
      http.post('*/api/public/reviews', () =>
        HttpResponse.json(
          { success: false, data: null, error: 'Too many reviews submitted. Please try again in an hour.' },
          { status: 429 },
        )),
    )
    const user = userEvent.setup()
    const screen = renderWithProviders(<SubmitReviewPage />, { route: '/review' })

    await fillValidForm(user, screen)
    await user.click(screen.getByRole('button', { name: /submit review/i }))

    expect(await screen.findByText(/try again in an hour/i)).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd frontend && pnpm test -- SubmitReviewPage`
Expected: FAIL — cannot resolve `./SubmitReviewPage`.

- [ ] **Step 3: Write the component**

```tsx
import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Alert, Button, Card, Form, Input, Rate, Result, Upload } from 'antd'
import type { UploadFile } from 'antd'
import { reviewsApi } from '../../api/reviews'
import PageHeader from '../../components/common/PageHeader'
import type { SubmitReviewRequest } from '../../types/review'

const MAX_IMAGES = 3
const MAX_IMAGE_BYTES = 5 * 1024 * 1024
const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp']

export default function SubmitReviewPage() {
  const [form] = Form.useForm<SubmitReviewRequest>()
  const [fileList, setFileList] = useState<UploadFile[]>([])
  const [fileError, setFileError] = useState<string | null>(null)
  const [submitted, setSubmitted] = useState(false)

  const mutation = useMutation({
    mutationFn: (values: SubmitReviewRequest) =>
      reviewsApi.submit(values, fileList.map(f => f.originFileObj as File).filter(Boolean)),
    onSuccess: () => setSubmitted(true),
  })

  function validateBeforeUpload(file: File): boolean {
    if (!ALLOWED_TYPES.includes(file.type)) {
      setFileError('Only JPEG, PNG, and WebP photos are allowed')
      return false
    }
    if (file.size > MAX_IMAGE_BYTES) {
      setFileError('Each photo must be smaller than 5MB')
      return false
    }
    setFileError(null)
    return false
  }

  if (submitted) {
    return (
      <Result
        status="success"
        title="Thank you for your review"
        subTitle="It will appear on our reviews page once it has been approved."
      />
    )
  }

  const serverError = mutation.isError
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    ? ((mutation.error as any)?.response?.data?.error ?? 'Could not submit your review. Please try again.')
    : null

  return (
    <div>
      <PageHeader title="Leave a Review" />

      <Card style={{ maxWidth: 560 }}>
        {serverError && <Alert type="error" message={serverError} style={{ marginBottom: 16 }} />}

        <Form form={form} layout="vertical" onFinish={values => mutation.mutate(values)}>
          <Form.Item
            name="reviewerName"
            label="Your name"
            rules={[{ required: true, message: 'Name is required' }, { max: 80 }]}
          >
            <Input maxLength={80} />
          </Form.Item>

          <Form.Item
            name="phone"
            label="Mobile number"
            rules={[
              { required: true, message: 'Mobile number is required' },
              { pattern: /^[6-9]\d{9}$/, message: 'Enter a valid 10-digit Indian mobile number' },
            ]}
            extra="We use this only to verify your rental. It is never shown publicly."
          >
            <Input maxLength={10} inputMode="numeric" />
          </Form.Item>

          <Form.Item
            name="itemDescription"
            label="What did you rent?"
            rules={[{ required: true, message: 'Tell us what you rented' }, { max: 100 }]}
          >
            <Input maxLength={100} placeholder="e.g. Red bridal lehenga" />
          </Form.Item>

          <Form.Item name="rating" label="Rating" rules={[{ required: true, message: 'Please give a rating' }]}>
            <Rate />
          </Form.Item>

          <Form.Item
            name="reviewText"
            label="Your review"
            rules={[{ required: true, message: 'Review cannot be empty' }]}
          >
            <Input.TextArea rows={4} maxLength={256} showCount />
          </Form.Item>

          <Form.Item label="Photos (optional, up to 3)">
            <Upload
              listType="picture-card"
              maxCount={MAX_IMAGES}
              fileList={fileList}
              beforeUpload={validateBeforeUpload}
              onChange={({ fileList: next }) => setFileList(next)}
              accept="image/jpeg,image/png,image/webp"
            >
              {fileList.length < MAX_IMAGES && '+ Add'}
            </Upload>
            {fileError && <Alert type="error" message={fileError} showIcon />}
          </Form.Item>

          <Button type="primary" htmlType="submit" loading={mutation.isPending}>
            Submit review
          </Button>
        </Form>
      </Card>
    </div>
  )
}
```

`beforeUpload` returns `false` in every branch so AntD never uploads on its own — files are held
client-side and sent with the form. Returning `false` on the rejection paths also keeps the
invalid file out of `fileList`.

- [ ] **Step 4: Add the route**

In `frontend/src/App.tsx`, next to the `/reviews` route:

```tsx
<Route path="/review" element={<PublicLayout><SubmitReviewPage /></PublicLayout>} />
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd frontend && pnpm test -- SubmitReviewPage`
Expected: PASS, 5 tests.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/public/SubmitReviewPage.tsx \
        frontend/src/pages/public/SubmitReviewPage.test.tsx \
        frontend/src/App.tsx
git commit -m "feat(review): public review submission form with photo and length limits"
```

---

### Task 12: Moderation page and navigation

**Files:**
- Create: `frontend/src/pages/reviews/ReviewModerationPage.tsx`
- Test: `frontend/src/pages/reviews/ReviewModerationPage.test.tsx`
- Modify: `frontend/src/components/layout/AppLayout.tsx`
- Modify: `frontend/src/components/layout/Sidebar.tsx`

**Interfaces:**
- Consumes: `reviewsApi.listForModeration`, `updateStatus`, `remove` (Task 9).
- Produces: default-exported `ReviewModerationPage`; nav entries.

- [ ] **Step 1: Write the failing test**

```tsx
import { describe, it, expect } from 'vitest'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { renderWithProviders, waitFor } from '../../test/render'
import { server } from '../../test/server'
import ReviewModerationPage from './ReviewModerationPage'

const pendingReview = {
  id: 'review-1',
  reviewerName: 'Priya S',
  phone: '9876543210',
  itemDescription: 'Red bridal lehenga',
  rating: 5,
  reviewText: 'Beautiful outfit.',
  status: 'PENDING',
  createdAt: '2026-09-20T14:30:00+05:30',
  moderatedAt: null,
  images: [],
}

function queueHandler(onRequest?: (url: URL) => void) {
  return http.get('*/api/reviews', ({ request }) => {
    onRequest?.(new URL(request.url))
    return HttpResponse.json({
      success: true,
      data: { content: [pendingReview], totalElements: 1, totalPages: 1, number: 0, size: 20 },
      error: null,
    })
  })
}

describe('ReviewModerationPage', () => {
  it('defaults the status filter to Pending', async () => {
    const seen: string[] = []
    server.use(queueHandler(url => seen.push(url.searchParams.get('status') ?? 'MISSING')))

    renderWithProviders(<ReviewModerationPage />, { route: '/reviews/manage' })

    await waitFor(() => expect(seen).toContain('PENDING'))
  })

  it('shows the reviewer phone number to the owner', async () => {
    server.use(queueHandler())

    const { findByText } = renderWithProviders(<ReviewModerationPage />, { route: '/reviews/manage' })

    expect(await findByText('9876543210')).toBeInTheDocument()
  })

  it('approving a review sends APPROVED and refetches the queue', async () => {
    let patched: unknown = null
    let queueCalls = 0
    server.use(
      queueHandler(() => { queueCalls += 1 }),
      http.patch('*/api/reviews/review-1/status', async ({ request }) => {
        patched = await request.json()
        return HttpResponse.json({ success: true, data: { ...pendingReview, status: 'APPROVED' }, error: null })
      }),
    )
    const user = userEvent.setup()
    const { findByRole } = renderWithProviders(<ReviewModerationPage />, { route: '/reviews/manage' })

    await user.click(await findByRole('button', { name: /approve/i }))

    await waitFor(() => expect(patched).toEqual({ status: 'APPROVED' }))
    await waitFor(() => expect(queueCalls).toBeGreaterThan(1))
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd frontend && pnpm test -- ReviewModerationPage`
Expected: FAIL — cannot resolve `./ReviewModerationPage`.

- [ ] **Step 3: Write the component**

```tsx
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Popconfirm, Rate, Segmented, Space, Table, Typography } from 'antd'
import { reviewsApi } from '../../api/reviews'
import PageHeader from '../../components/common/PageHeader'
import type { AdminReview, ReviewStatus } from '../../types/review'

const { Text, Paragraph } = Typography

const PAGE_SIZE = 20
const FILTERS: { label: string; value: ReviewStatus | 'ALL' }[] = [
  { label: 'Pending', value: 'PENDING' },
  { label: 'Approved', value: 'APPROVED' },
  { label: 'Rejected', value: 'REJECTED' },
  { label: 'All', value: 'ALL' },
]

export default function ReviewModerationPage() {
  const [filter, setFilter] = useState<ReviewStatus | 'ALL'>('PENDING')
  const [page, setPage] = useState(0)
  const queryClient = useQueryClient()

  const { data, isLoading } = useQuery({
    queryKey: ['admin-reviews', filter, page],
    queryFn: () => reviewsApi.listForModeration({
      status: filter === 'ALL' ? undefined : filter,
      page,
      size: PAGE_SIZE,
    }),
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin-reviews'] })

  const moderate = useMutation({
    mutationFn: ({ id, status }: { id: string; status: ReviewStatus }) => reviewsApi.updateStatus(id, status),
    onSuccess: invalidate,
  })

  const remove = useMutation({
    mutationFn: (id: string) => reviewsApi.remove(id),
    onSuccess: invalidate,
  })

  const columns = [
    { title: 'Name', dataIndex: 'reviewerName', key: 'reviewerName' },
    { title: 'Phone', dataIndex: 'phone', key: 'phone' },
    { title: 'Item', dataIndex: 'itemDescription', key: 'itemDescription' },
    {
      title: 'Rating',
      dataIndex: 'rating',
      key: 'rating',
      render: (rating: number) => <Rate disabled value={rating} />,
    },
    {
      title: 'Review',
      dataIndex: 'reviewText',
      key: 'reviewText',
      render: (text: string) => <Paragraph style={{ marginBottom: 0, maxWidth: 320 }}>{text}</Paragraph>,
    },
    {
      title: 'Photos',
      key: 'images',
      render: (_: unknown, review: AdminReview) => (
        <Space>
          {review.images.map(image => (
            <img key={image.id} src={image.thumbnailUrl} alt="" width={48} height={48}
                 style={{ objectFit: 'cover', borderRadius: 6 }} />
          ))}
        </Space>
      ),
    },
    { title: 'Status', dataIndex: 'status', key: 'status', render: (s: ReviewStatus) => <Text>{s}</Text> },
    {
      title: 'Actions',
      key: 'actions',
      render: (_: unknown, review: AdminReview) => (
        <Space>
          <Button
            size="small"
            type="primary"
            disabled={review.status === 'APPROVED'}
            onClick={() => moderate.mutate({ id: review.id, status: 'APPROVED' })}
          >
            Approve
          </Button>
          <Button
            size="small"
            disabled={review.status === 'REJECTED'}
            onClick={() => moderate.mutate({ id: review.id, status: 'REJECTED' })}
          >
            Reject
          </Button>
          <Popconfirm
            title="Delete this review?"
            description="The review and its photos are removed permanently."
            onConfirm={() => remove.mutate(review.id)}
          >
            <Button size="small" danger>Delete</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <PageHeader title="Manage Reviews" />

      <Segmented
        options={FILTERS}
        value={filter}
        onChange={value => { setFilter(value as ReviewStatus | 'ALL'); setPage(0) }}
        style={{ marginBottom: 16 }}
      />

      <Table<AdminReview>
        rowKey="id"
        loading={isLoading}
        columns={columns}
        dataSource={data?.content ?? []}
        pagination={{
          current: page + 1,
          total: data?.totalElements ?? 0,
          pageSize: PAGE_SIZE,
          showSizeChanger: false,
          onChange: next => setPage(next - 1),
        }}
      />
    </div>
  )
}
```

- [ ] **Step 4: Add the route and nav entries**

In `frontend/src/components/layout/AppLayout.tsx`, alongside the other `OwnerRoute` routes:

```tsx
<Route path="/reviews/manage" element={<OwnerRoute><ReviewModerationPage /></OwnerRoute>} />
```

In `frontend/src/components/layout/Sidebar.tsx`, add to `NAV_ITEMS` after the Gallery entry:

```ts
  { key: '/reviews',        label: 'Reviews',        roles: ['OWNER', 'EXECUTIVE'] },
  { key: '/reviews/manage', label: 'Manage Reviews', roles: ['OWNER'] },
```

and to `PUBLIC_NAV_ITEMS` after the Gallery entry:

```ts
  { key: '/reviews', label: 'Reviews', target: '/reviews' },
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd frontend && pnpm test -- ReviewModerationPage`
Expected: PASS, 3 tests.

- [ ] **Step 6: Run the whole frontend suite and the type check**

Run: `cd frontend && pnpm type-check && pnpm lint && pnpm test`
Expected: PASS. `pages.render.test.tsx` renders every page — a new route that throws on mount
surfaces there, not in the page's own test file.

- [ ] **Step 7: Run the whole backend suite one final time**

Run: `cd backend && ./gradlew test integrationTest`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/pages/reviews/ \
        frontend/src/components/layout/AppLayout.tsx \
        frontend/src/components/layout/Sidebar.tsx
git commit -m "feat(review): owner moderation queue and review navigation entries"
```

---

## Manual Verification

Automated tests do not exercise real image compression — `ImageStorageService` is mocked
throughout. Run this once before opening the PR:

1. `docker-compose up -d && cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev'`
2. `cd frontend && pnpm dev`
3. Open `http://localhost:5173/review`, submit a review with a photo over 3MB.
4. Confirm the stored file under the local storage directory is **materially smaller** than the
   original and that a thumbnail exists beside it. This is the only check that proves the
   "stored in compressed format" requirement actually holds end to end.
5. Log in as OWNER, open `/reviews/manage`, approve the review.
6. Open `/reviews` in a private window (logged out) and confirm the review appears, the photo
   renders, and no phone number is visible. Check the Network tab: the JSON response must contain
   no phone field.

---

## Self-Review Notes

**Spec coverage:** Every acceptance criterion in
`features/07-reviews/US-801-803-customer-reviews.md` maps to a task — US-801 to Tasks 1–7 and 11,
US-802 to Tasks 6–8 and 10, US-803 to Tasks 6–8 and 12. The one spec item not covered by an
automated test is real image compression, which is why the Manual Verification section exists.

**Deliberately out of scope**, matching the spec: no average-rating summary endpoint, and no
Playwright e2e test.
