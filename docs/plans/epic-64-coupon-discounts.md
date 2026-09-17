# Epic #64 — Coupon codes at checkout: implementation plan

## 1. Scope statement

### Ships now
Rent-subtotal discounting only, end to end: coupon domain model, OWNER admin CRUD, checkout validation + application, receipt snapshot, frontend (checkout input + management page), and a discounts-given report.

### Explicitly deferred
Sale-subtotal discounting. Epic #57 (dual-mode sell items) is **not implemented** — verified: `Item.java` has no `isSellable`/`salePrice`, `ReceiptLineItem.java` has no `lineType`, and `CheckoutService` has no SALE-line concept (`backend/src/main/java/com/fashionrental/receipt/CheckoutService.java` sums exactly two buckets, `totalRent` and `totalDeposit`). Building `appliesTo` branching now would be speculative code against a schema that does not exist.

### What "designed to extend cleanly" means, concretely

Four specific seams, not a vague promise:

1. **`DiscountableSubtotals(int rent, int sale)`** — a record in `com.fashionrental.receipt`, with `int total() { return rent + sale; }`. Today `CheckoutService` always constructs `new DiscountableSubtotals(totalRent, 0)`. When #57 ships, the `0` becomes `totalSale`. That is the entire change to the discount input.
2. **`CouponDiscountResolver.resolve(String rawCode, DiscountableSubtotals subtotals)`** — the single home of all coupon validation and discount math. #57 does not touch this class at all. Its signature is already sale-aware.
3. **`receipts.discount_amount` is receipt-level, not line-level.** The AC ("receipt persists coupon code + discount amount") implies this, and `ReceiptLineItem` has no discount concept to extend. When SALE lines appear, the persisted shape is unchanged — no migration, no backfill.
4. **The deposit is excluded by the type system, not by an `if`.** `DiscountableSubtotals` has no deposit field and never will. There is no code path where a deposit value can reach `CouponDiscountResolver`. The "never discount the deposit" AC is structurally impossible to violate, which is stronger than a guarded conditional that a future refactor could delete.

**Deliberately NOT adding an `appliesTo` column now.** The epic text lists it; sub-issue #65's field list omits it. A `NOT NULL` column with exactly one legal value (`RENT_AND_SALE`) is a constant masquerading as data, and `ddl-auto: validate` forces an entity field to match it that nothing reads. If a rent-only coupon type is ever wanted, the later migration is one strictly-additive line:
```sql
ALTER TABLE coupons ADD COLUMN applies_to VARCHAR(16) NOT NULL DEFAULT 'RENT_AND_SALE';
```
Zero backfill risk, zero data loss. *(Flagged in §8 — a reviewer may want the column up front; the cost of being wrong is that one ALTER.)*

---

## 2. Data model (#65)

### `com.fashionrental.configuration.Coupon`

Placed in `configuration/` alongside `LateFeeRule` — it is owner-configured shop policy, and it inherits `/api/config/**` → `hasRole("OWNER")` from `SecurityConfig`.

| Java field | Type | Column | Notes |
|---|---|---|---|
| `id` | `UUID` | `id` | `@GeneratedValue(strategy = GenerationType.UUID)` — matches `Receipt`/`LateFeeRule` |
| `code` | `String` | `code` | `@Column(nullable=false, unique=true, length=32)`; **stored normalised: trimmed + uppercased** |
| `discountType` | `DiscountType` enum `{PERCENT, FIXED}` | `discount_type` | `@Enumerated(EnumType.STRING)`, `length=16` |
| `value` | `int` | `value` | primitive — never null |
| `minSubtotal` | `Integer` | `min_subtotal` | **boxed** — nullable |
| `validFrom` | `OffsetDateTime` | `valid_from` | `TIMESTAMPTZ` |
| `validTo` | `OffsetDateTime` | `valid_to` | `TIMESTAMPTZ` |
| `usageLimit` | `Integer` | `usage_limit` | **boxed** — nullable |
| `timesUsed` | `int` | `times_used` | primitive, default 0 |
| `isActive` | `boolean` | `is_active` | default `true` |
| `createdAt` / `updatedAt` | `OffsetDateTime` | `created_at` / `updated_at` | `@PrePersist`/`@PreUpdate`, mirroring `Receipt.java` |

**Annotate the class `@DynamicUpdate`.** Non-obvious but load-bearing: by default Hibernate writes *every* column on a dirty-check flush, so an admin `PUT` that never touches `timesUsed` would still write the entity's loaded `times_used` value back — stomping any increment a concurrent checkout committed in between. `@DynamicUpdate` restricts the UPDATE to changed columns, making the admin path and the checkout path touch genuinely disjoint columns.

**Money:** `value`, `minSubtotal`, `timesUsed`, `usageLimit` are all `int`/`Integer` / `INTEGER` per CLAUDE.md. No `BigDecimal`, no float. (Note `LateFeeRule` uses `BigDecimal` for `penaltyMultiplier` — that is a multiplier, not money; do not copy it here.)

### `Receipt` additions
```
@Column(name = "coupon_code", length = 32)      private String couponCode;      // null = no coupon
@Column(name = "discount_amount", nullable = false) private int discountAmount = 0;
```
Plus getters/setters in the existing style. **No FK to `coupons`** — deliberate: the receipt must survive a coupon row being edited or removed, and must record the code as it was at checkout. The code is a snapshot string, exactly like `rateSnapshot`/`depositSnapshot` on `ReceiptLineItem`.

### `CouponRepository`
Mirrors `LateFeeRuleRepository` (`backend/.../configuration/LateFeeRuleRepository.java`):
```java
public interface CouponRepository extends JpaRepository<Coupon, UUID> {
    Optional<Coupon> findByCode(String code);          // code is pre-normalised by the caller
    boolean existsByCode(String code);
    List<Coupon> findAllByOrderByCreatedAtDesc();
    List<Coupon> findByIsActiveTrueOrderByCreatedAtDesc();

    @Modifying(flushAutomatically = true)
    @Query("UPDATE Coupon c SET c.timesUsed = c.timesUsed + 1 "
         + "WHERE c.id = :id AND (c.usageLimit IS NULL OR c.timesUsed < c.usageLimit)")
    int incrementTimesUsed(@Param("id") UUID id);
}
```
Note `clearAutomatically` is deliberately **not** set — clearing the persistence context mid-`createReceipt` would detach the in-flight `Receipt` aggregate. Safe to omit because `CheckoutService` never mutates the `Coupon` entity in Java, so there is no dirty copy to flush back.

A JPQL bulk update does not fire `@PreUpdate`, so `updated_at` will not move when `times_used` increments. That is the desired semantic: `updated_at` tracks owner edits, not redemptions.

### Migration: `backend/src/main/resources/db/migration/V20260917001__add_coupons_and_receipt_discount.sql`

Follows `V<YYYYMMDD><NNN>__<description>.sql`; next after `V20260915001__add_is_ad_hoc_to_items.sql`. One file for both changes — they are one logical unit and one AC.

```sql
-- Coupons discount the RENT subtotal of a receipt. The refundable deposit is never
-- discounted: the deposit is not a field on DiscountableSubtotals, so it cannot reach
-- the discount calculation at all. Sale-subtotal discounting waits on #57.
CREATE TABLE coupons (
    id            UUID        PRIMARY KEY,
    code          VARCHAR(32) NOT NULL,
    discount_type VARCHAR(16) NOT NULL,
    value         INTEGER     NOT NULL,
    min_subtotal  INTEGER,
    valid_from    TIMESTAMPTZ NOT NULL,
    valid_to      TIMESTAMPTZ NOT NULL,
    usage_limit   INTEGER,
    times_used    INTEGER     NOT NULL DEFAULT 0,
    is_active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT coupons_discount_type_check   CHECK (discount_type IN ('PERCENT', 'FIXED')),
    CONSTRAINT coupons_value_positive_check  CHECK (value > 0),
    CONSTRAINT coupons_percent_range_check   CHECK (discount_type <> 'PERCENT' OR value BETWEEN 1 AND 100),
    CONSTRAINT coupons_min_subtotal_check    CHECK (min_subtotal IS NULL OR min_subtotal > 0),
    CONSTRAINT coupons_usage_limit_check     CHECK (usage_limit IS NULL OR usage_limit > 0),
    CONSTRAINT coupons_times_used_check      CHECK (times_used >= 0),
    CONSTRAINT coupons_validity_range_check  CHECK (valid_to > valid_from)
);

-- Codes are normalised (trimmed + uppercased) before persistence, so a plain unique
-- index is sufficient for case-insensitive uniqueness.
CREATE UNIQUE INDEX ux_coupons_code ON coupons (code);

ALTER TABLE receipts
    ADD COLUMN coupon_code     VARCHAR(32),
    ADD COLUMN discount_amount INTEGER NOT NULL DEFAULT 0;

ALTER TABLE receipts
    ADD CONSTRAINT receipts_discount_amount_check
        CHECK (discount_amount >= 0),
    -- A discount without a coupon is impossible. A coupon with a zero discount is legal
    -- (1% of a small subtotal floors to 0), so this is deliberately one-directional.
    ADD CONSTRAINT receipts_discount_requires_coupon_check
        CHECK (coupon_code IS NOT NULL OR discount_amount = 0),
    ADD CONSTRAINT receipts_grand_total_check
        CHECK (grand_total = total_rent - discount_amount + total_deposit);
```

**Two implementer checks before merging:**
- *`id UUID PRIMARY KEY` with no `DEFAULT`* assumes Hibernate supplies the UUID (consistent with `@GeneratedValue(strategy = GenerationType.UUID)` on `Receipt`/`LateFeeRule`). Confirm against the initial-schema migration and match it exactly.
- *`receipts_grand_total_check`* is added over existing data. Existing rows have `discount_amount = 0` and `grand_total = total_rent + total_deposit`, so it should validate. If it fails in any environment, that environment has a pre-existing receipts integrity bug worth knowing about. This constraint is recommended but droppable if the tech-lead persona judges it too rigid — note that #57 will need to ALTER it to include `total_sale`.

No index on `coupons.is_active` or `valid_from/valid_to`: single-shop scale, tens of rows. `ux_coupons_code` is the only lookup path the checkout uses.

**#65 AC coverage:** migration applies (integration test boots the container); entities validate (`ddl-auto: validate` means the app refuses to start on any mismatch — this is self-enforcing); code uniqueness enforced by `ux_coupons_code` plus an integration test asserting the constraint violation.

---

## 3. Backend: admin CRUD (#66)

### API shape decision: per-entity REST, **not** replace-all

#66's text says "mirroring LateFeeRule structure". Read literally that means copying `PUT /api/config/late-fee-rules`, which `ConfigService.updateLateFeeRules` implements as `deactivateAll()` followed by re-saving the whole submitted list. **That pattern is wrong for coupons.** Three concrete reasons:

1. **Lost-update on a live counter.** `times_used` is mutated by the checkout path. A replace-all PUT built from an admin page loaded five minutes ago would write back a stale `times_used`, silently resurrecting spent coupon capacity. `LateFeeRule` has no mutable counter, which is why replace-all is safe there and not here.
2. **`deactivateAll()` is data loss here.** Retired coupons must be retained — `receipts.coupon_code` references them as a snapshot and the #69 report reads historical codes. A replace-all PUT would deactivate every coupon not present in the submitted page.
3. **Unbounded row count.** Late fee rules are a handful, edited as a set. Coupons accumulate monotonically. Shipping the entire list on every edit is an O(n) payload for an O(1) change, and grows without bound.

**What we *do* mirror from `LateFeeRule`:** the module (`configuration/`), the entity + `JpaRepository` + `@Service` + `@RestController` layering, the `configuration/model/` flat DTO package (note: `configuration` uses a flat `model/` package, not `model/request` + `model/response` — match the module you are in), the `ApiResponse<T>` envelope, `@Tag`/`@Operation` Swagger annotations, and `ValidationException` → `ApiResponse.error` via `GlobalExceptionHandler`. We mirror the *structure*; we decline the *verb*. **Say this explicitly in the #66 PR description** so the deviation from the issue text is a visible decision rather than a silent one.

### Endpoints — `CouponController` (new, `configuration/`)

Keep `ConfigController` as-is (late fee rules) and add a sibling controller; a single controller owning two unrelated aggregates would be the same grab-bag smell as `SettingsPage`.

| Verb | Path | Body | Returns |
|---|---|---|---|
| GET | `/api/config/coupons?includeInactive=false` | — | `ApiResponse<List<CouponResponse>>` |
| GET | `/api/config/coupons/{id}` | — | `ApiResponse<CouponResponse>` |
| POST | `/api/config/coupons` | `CreateCouponRequest` | 201 `ApiResponse<CouponResponse>` |
| PUT | `/api/config/coupons/{id}` | `UpdateCouponRequest` | `ApiResponse<CouponResponse>` |
| PATCH | `/api/config/coupons/{id}/status` | `SetCouponStatusRequest` | `ApiResponse<CouponResponse>` |

**Why `PATCH /status` and not `DELETE`-as-deactivate:** there is repo precedent for DELETE-as-deactivate (`authApi.deleteUser` → "User deactivated"), but it is dishonest naming and cannot reactivate. `PATCH /status` is explicit, bidirectional, and cheap. **`isActive` is deliberately absent from `UpdateCouponRequest`** — PUT edits the coupon's terms, PATCH flips the switch. One way to do each thing.

**Security wiring: none needed.** `SecurityConfig` already has `.requestMatchers("/api/config/**").hasRole("OWNER")` ahead of the `/api/**` catch-all. EXECUTIVE gets 403 on every verb above, for free. This is the single strongest reason to site coupons under `/api/config` rather than a new top-level path. (`#66`'s "EXECUTIVE blocked" AC is satisfied by placement; still worth an integration test to prevent a future `SecurityConfig` edit from regressing it.)

### DTOs — `configuration/model/`

```
CreateCouponRequest(
    @NotBlank @Pattern(regexp="^[A-Za-z0-9_-]{3,32}$", message="Code must be 3-32 letters, digits, hyphens or underscores") String code,
    @NotNull Coupon.DiscountType discountType,
    @NotNull @Min(1) Integer value,
    @Min(1) Integer minSubtotal,        // nullable
    @NotNull OffsetDateTime validFrom,
    @NotNull OffsetDateTime validTo,
    @Min(1) Integer usageLimit)         // nullable

UpdateCouponRequest(  // identical minus `code` and minus `isActive`
    @NotNull Coupon.DiscountType discountType,
    @NotNull @Min(1) Integer value,
    @Min(1) Integer minSubtotal,
    @NotNull OffsetDateTime validFrom,
    @NotNull OffsetDateTime validTo,
    @Min(1) Integer usageLimit)

SetCouponStatusRequest(@NotNull Boolean isActive)

CouponResponse(UUID id, String code, String discountType, int value,
               Integer minSubtotal, OffsetDateTime validFrom, OffsetDateTime validTo,
               Integer usageLimit, int timesUsed, boolean isActive,
               OffsetDateTime createdAt, OffsetDateTime updatedAt)
```

`timesUsed` appears on the **response only, never on a request.** That is the primary defence against an admin write clobbering the counter; `@DynamicUpdate` is the secondary one.

**Code is immutable after first use.** `UpdateCouponRequest` omits `code` entirely, so a code can never change post-creation. Rationale: `receipts.coupon_code` is a snapshot string with no FK, so renaming would split a coupon's history across two strings in the #69 report. Omitting the field (rather than conditionally rejecting) makes the rule un-bypassable. If the owner needs a different code, they deactivate and create a new one.

### `CouponService` methods

```java
@Service @Transactional
class CouponService {
    List<CouponResponse> listCoupons(boolean includeInactive);
    CouponResponse getCoupon(UUID id);
    CouponResponse createCoupon(CreateCouponRequest request);
    CouponResponse updateCoupon(UUID id, UpdateCouponRequest request);
    CouponResponse setStatus(UUID id, SetCouponStatusRequest request);
    private CouponResponse toResponse(Coupon coupon);   // mirrors ConfigService#toResponse
}
```

### Validation rules (service layer, beyond Bean Validation)

Bean Validation covers `@NotBlank`/`@NotNull`/`@Min`/`@Pattern` and produces a 400 via `GlobalExceptionHandler`'s `MethodArgumentNotValidException` handler. Cross-field rules live in the service and throw `ValidationException` (→ 400 + `ApiResponse.error`):

| Rule | Message |
|---|---|
| PERCENT and `value` outside 1–100 | `"A percentage discount must be between 1 and 100."` |
| `validTo` not after `validFrom` | `"Coupon validity end must be after its start."` |
| Duplicate code (after normalisation) | `"A coupon with code 'SAVE20' already exists."` — throw `ConflictException` → **409**, matching the repo's `ConflictException` semantics. `ux_coupons_code` is the backstop for the check-then-insert race. |
| Coupon not found on GET/PUT/PATCH | `ResourceNotFoundException("Coupon not found: " + id)` → 404, matching `ReceiptService#getReceipt` |

**Allowed, deliberately:** lowering `usageLimit` below the current `timesUsed`. It simply means no further uses — rejecting it would prevent an owner from cutting off a coupon that is being abused. Worth a one-line `// Why` comment.

**Normalisation:** `code.trim().toUpperCase(Locale.ROOT)` applied once in `createCoupon` before the `existsByCode` check and before persistence. `Locale.ROOT` matters — Turkish-locale `toUpperCase` maps `i` to `İ`.

---

## 4. Backend: checkout integration (#67)

### Request/response DTO changes

`receipt/model/request/CheckoutPreviewRequest.java` — add trailing field:
```java
public record CheckoutPreviewRequest(
    @NotNull OffsetDateTime startDatetime,
    @NotNull OffsetDateTime endDatetime,
    @Valid List<CheckoutLineItem> items,
    List<@NotNull @Valid AdHocLineItem> adHocItems,
    String couponCode)                                  // nullable; blank == absent
```
`receipt/model/request/CheckoutRequest.java` — same trailing `String couponCode` addition. Both records have compact constructors normalising null lists; leave those untouched. No `@NotBlank` on `couponCode` — absence is the normal case.

`receipt/model/response/CheckoutPreviewResponse.java`:
```java
public record CheckoutPreviewResponse(
    boolean allAvailable, List<PreviewLineItem> lineItems, int rentalDays,
    int totalRent,
    String couponCode,          // NEW — echoes the normalised code, null when none
    int discountAmount,         // NEW
    int totalDeposit, int grandTotal, List<String> unavailableItems)
```
`receipt/model/response/ReceiptResponse.java` — add `String couponCode` and `int discountAmount` after `totalRent`.
`receipt/model/response/ReceiptSummaryResponse.java` — same two fields (the receipts list shows `grandTotal`; without the discount the numbers look wrong).

`ReceiptMapper.toReceiptResponse` (`backend/.../receipt/ReceiptMapper.java`) — thread `receipt.getCouponCode()` and `receipt.getDiscountAmount()` into the constructor. `ReceiptService#toSummaryResponse` — same.

`PreviewLineItem` and `ReceiptLineItemResponse` are **unchanged**. The discount is receipt-level; there is no line-level allocation.

### The shared discount logic — `receipt/CouponDiscountResolver.java`

This is the one place the math lives. Both `preview()` and `createReceipt()` call it; neither reimplements anything.

```java
@Component
public class CouponDiscountResolver {
    private final CouponRepository couponRepository;
    private final Clock clock;   // inject a Clock so validity-window tests don't sleep

    public AppliedDiscount resolve(String rawCode, DiscountableSubtotals subtotals);

    // Package-private and pure — unit-tested directly, no repository needed.
    static int computeDiscountAmount(Coupon coupon, int discountableSubtotal);
}
```

Supporting records, same package:
```java
public record DiscountableSubtotals(int rent, int sale) {
    public int total() { return rent + sale; }
}

public record AppliedDiscount(Coupon coupon, String code, int amount) {
    public static AppliedDiscount none() { return new AppliedDiscount(null, null, 0); }
    public boolean isApplied() { return coupon != null; }
}
```

`CheckoutService` gains two injected collaborators (`CouponDiscountResolver`, `CouponRepository`) and one private method:
```java
// Only the rent subtotal is discountable today. When #57 lands, the hardcoded 0 becomes
// the sale subtotal — that is the entire change; CouponDiscountResolver is untouched.
private DiscountableSubtotals discountableSubtotals(int totalRent) {
    return new DiscountableSubtotals(totalRent, 0);
}
```

**In `preview()`** — replace `int grandTotal = totalRent + totalDeposit;` with:
```java
AppliedDiscount discount = couponDiscountResolver.resolve(
        request.couponCode(), discountableSubtotals(totalRent));
int grandTotal = totalRent - discount.amount() + totalDeposit;
```
then pass `discount.code()` and `discount.amount()` into the response. **Preview never increments `timesUsed`.**

**In `createReceipt()`** — after the ad-hoc loop, replacing `receipt.setGrandTotal(totalRent + totalDeposit)`:
```java
AppliedDiscount discount = couponDiscountResolver.resolve(
        request.couponCode(), discountableSubtotals(totalRent));
if (discount.isApplied()) {
    claimCouponUsage(discount.coupon());       // conditional UPDATE, see below
}
receipt.setTotalRent(totalRent);
receipt.setTotalDeposit(totalDeposit);
receipt.setCouponCode(discount.code());
receipt.setDiscountAmount(discount.amount());
receipt.setGrandTotal(totalRent - discount.amount() + totalDeposit);
```

`totalRent` stays **gross**. The discount is a separate, visible number, which keeps the `grand_total = total_rent - discount_amount + total_deposit` identity checkable in SQL and keeps the receipt auditable.

### Ad-hoc items: **yes**, they count toward the discountable subtotal

`totalRent` in `createReceipt()` already includes ad-hoc `lineRent` (the loop at the ad-hoc section adds `line.getLineRent()` to `totalRent`). Reasoning:
- Ad-hoc lines are RENT lines. They are persisted as ordinary `ReceiptLineItem` rows against real `items` rows (`is_ad_hoc = true`), and they contribute to `receipts.total_rent`.
- The coupon's promise is "X% off your rent". Excluding ad-hoc lines would require computing the discount from a *subset* of `total_rent`, which breaks the grand-total identity and makes the receipt impossible to reconcile by inspection.
- Counter-argument (flagged in §8): ad-hoc items are owner-typed one-off prices, already discretionary, so a coupon on top is a double discount. That is a pricing-discipline question for the owner, not a system constraint — and the same owner applies both.

### Rounding rule: **floor, then clamp**

```
PERCENT:  amount = (discountableSubtotal * value) / 100     // Java int division == floor; both operands non-negative
FIXED:    amount = value
then:     amount = Math.min(amount, discountableSubtotal)
```

**Why floor and not round-half-up:** floor guarantees the discount never exceeds the stated percentage. "10% off" on ₹1505 yields ₹150, not ₹151. The shop is never surprised by giving away a rupee more than advertised, and the rule is trivially explainable to staff ("we round in the shop's favour"). It is also a single integer expression with no `double` anywhere, satisfying CLAUDE.md's no-float rule without a rounding-mode argument. (`CheckoutService#derivePerDayRate` uses `Math.round` on a `double`, but that derives a display/late-fee basis, not a charged amount — do not copy it here.)

**The clamp is not optional.** A ₹500 FIXED coupon on a ₹300 rent subtotal must yield ₹300, not ₹500 — otherwise `grand_total` would dip below `total_deposit` and the `receipts_grand_total_check` constraint plus the deposit-refund math would both be wrong. `Math.min` makes a negative net rent unreachable.

**A valid coupon may compute to ₹0** (1% of a ₹50 subtotal). This is allowed, not an error: the coupon *is* valid, the arithmetic just floors to zero. The receipt stores `coupon_code = 'X', discount_amount = 0` — which is exactly why `receipts_discount_requires_coupon_check` is one-directional. The UI shows "₹0 off". Document this; it will otherwise be reported as a bug.

### Validation order and exact rejection messages

All are `ValidationException` → **400** + `ApiResponse.error(message)` via `GlobalExceptionHandler`, except where noted. Checked in this exact order so the message is deterministic:

| # | Check | Message |
|---|---|---|
| 0 | `rawCode` null / blank | *(not an error)* → `AppliedDiscount.none()`, returns immediately |
| 1 | Normalise: `trim().toUpperCase(Locale.ROOT)` | — |
| 2 | `findByCode` empty | `"Coupon code 'SAVE20' is not valid."` |
| 3 | `!isActive` | `"Coupon 'SAVE20' is no longer active."` |
| 4 | `now.isBefore(validFrom)` | `"Coupon 'SAVE20' is not valid yet — it becomes active on 20 Sep 2026."` |
| 5 | `now.isAfter(validTo)` | `"Coupon 'SAVE20' expired on 15 Sep 2026."` |
| 6 | `usageLimit != null && timesUsed >= usageLimit` | `"Coupon 'SAVE20' has reached its usage limit."` |
| 7 | `minSubtotal != null && subtotals.total() < minSubtotal` | `"Coupon 'SAVE20' requires a minimum rent subtotal of ₹2,000. This order's rent subtotal is ₹1,450."` |
| 8 | compute + clamp | — |

Deliberately *not* merged into one generic "Invalid coupon" — the AC says "rejected with a clear message", and a staff member at a counter needs to know *which* condition failed to explain it to a customer. Dates formatted IST (`dd MMM yyyy`) to match the app's timezone convention. `now` comes from the injected `Clock`, not `OffsetDateTime.now()`, so the window tests are deterministic.

Checks 3–6 use the coupon's state *as read*; check 6 is re-verified atomically at claim time (below), so a read-time pass is an optimistic pre-check that produces the friendly message in the common case.

### `timesUsed` increment and concurrency

```java
private void claimCouponUsage(Coupon coupon) {
    int claimed = couponRepository.incrementTimesUsed(coupon.getId());
    if (claimed == 0) {
        throw new ConflictException(
            "Coupon '" + coupon.getCode() + "' has reached its usage limit.");
    }
}
```

**Is a race on the last usage slot possible? No — and here is the guard.** The increment is a single conditional `UPDATE ... WHERE id = ? AND (usage_limit IS NULL OR times_used < usage_limit)`. Under Postgres READ COMMITTED (the default), when two concurrent transactions target the same row, the second `UPDATE` blocks on the row lock until the first commits, then **re-evaluates its `WHERE` clause against the newly committed row version** (EvalPlanQual). So the loser sees `times_used = 1, usage_limit = 1`, matches zero rows, gets `claimed == 0`, and throws. There is no read-then-write window to lose, and no pessimistic lock or `@Version` is needed. The `ConflictException` → 409 distinguishes "lost the race" from the 400 pre-check, which is honest.

`claimCouponUsage` runs **inside `createReceipt`'s existing `@Transactional`** — so if a later availability `ConflictException` rolls the receipt back, the increment rolls back with it. No orphaned redemptions. Call it after resolution and before `receiptRepository.save`, so the slot is claimed as early as possible and the race window is minimal.

**Duplicate submit:** a genuine double-tap creates two receipts and burns two coupon uses. This is a **pre-existing gap** — `POST /api/receipts` has no idempotency key today, and a double-tap already double-books inventory. Not introduced by this epic, but the coupon makes it more visible. Current mitigation is frontend-only (`createMutation.isPending` disables the Create Receipt button in `CheckoutPage.tsx`). Flagged in §8; out of scope here.

### One downstream change: `Invoice.totalRent`

`ReturnService.processReturn` sets `invoice.setTotalRent(receipt.getTotalRent())` — gross. Verified this value **feeds no arithmetic**: `depositToReturn` and `balanceOwed` derive only from `receipt.getTotalDeposit()` and the late-fee/damage totals, so the deposit refund is correctly unaffected by any discount. But the customer-facing invoice would display a rent figure higher than what they paid.

**Recommendation:** change it to `receipt.getTotalRent() - receipt.getDiscountAmount()` (one line + one test). Semantic change applies to new rows only; every historical row has `discount_amount = 0`, so existing invoices are numerically unchanged. Alternative (rejected as heavier): add a `discount_amount` column to `invoices` and render a separate line. Include this in #67, and call it out for the tech-lead persona.

---

## 5. Backend: reporting (#69)

### Endpoint
```
GET /api/reports/discounts-given?from=YYYY-MM-DD&to=YYYY-MM-DD
```
Inclusive date range, IST day boundaries. Defaults when omitted: `from` = first day of the current month, `to` = today — consistent with `ReportingController`'s existing "default to now" habit (`getDailyRevenue` defaults to `LocalDate.now()`). OWNER-only for free via `.requestMatchers("/api/reports/**").hasRole("OWNER")`. Read-only.

### Response DTOs — `reporting/model/response/`
```java
public record DiscountsGivenResponse(
        LocalDate from, LocalDate to,
        int totalDiscountGiven,
        int receiptsWithCoupon,
        List<CouponDiscountSummary> byCoupon) {}

public record CouponDiscountSummary(
        String couponCode, int timesApplied, int totalDiscount) {}
```

### Query approach
Reuse the existing `receiptRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(from, to)` and aggregate in Java with streams — **every** method in `ReportingService` already does exactly this (`getDailyRevenue`, `getMonthlyRevenue`, `getOutstandingDeposits`). #69's AC is "consistent with existing reporting module"; consistency wins over micro-optimisation at single-shop scale.

Boundary conversion mirrors `ReportingService`'s existing `IST` constant:
```java
OffsetDateTime rangeStart = from.atStartOfDay(IST).toOffsetDateTime();
OffsetDateTime rangeEnd   = to.plusDays(1).atStartOfDay(IST).toOffsetDateTime();   // exclusive
```

Aggregation detail that matters: **filter on `couponCode != null`, not on `discountAmount > 0`.** A coupon that floored to a ₹0 discount was still applied and belongs in `receiptsWithCoupon` / `timesApplied`; it just contributes 0 to the sums. Group by `couponCode`, sort `byCoupon` descending by `totalDiscount`.

Add a `// Why` note: if `receipts` ever grows past in-memory aggregation, swap for a `@Query` projection (`SELECT r.couponCode, COUNT(r), SUM(r.discountAmount) ... GROUP BY r.couponCode`). Not now.

### Required correction to the existing revenue reports

`getDailyRevenue` and `getMonthlyRevenue` compute `rentCollected` as `sum(Receipt::getTotalRent)` — **gross**. Once discounts exist, `netFlow = rentCollected + depositsCollected + collectedFromCustomers - depositsRefunded` overstates cash actually received.

Fix, additively:
- Add `int discountsGiven` to `DailyRevenueResponse`, `DailyRevenueSummary`, and `MonthlyRevenueResponse` (as `totalDiscountsGiven`).
- Leave `rentCollected` **gross** so the existing label stays truthful.
- Change `netFlow` to `rentCollected - discountsGiven + depositsCollected + collectedFromCustomers - depositsRefunded`.
- Mirror the new fields into `frontend/src/types/reports.ts` and render them in `ReportsPage`.

This changes numbers the owner already looks at. **Business-visible — flagged in §8.**

---

## 6. Frontend (#68)

### Key discovery that shapes this section

`CheckoutPage.tsx` **never calls the backend preview endpoint.** It computes every total client-side from the cart (`totalRent`/`totalDeposit`/`grandTotal` via `lineRentOf` from `cartPricing.ts`). `receiptsApi.preview` exists in `frontend/src/api/receipts.ts` and is entirely unused.

**Decision: no new endpoint. Start using `receiptsApi.preview`, coupon-only.** When the user clicks Apply on the preview screen, call `receiptsApi.preview({ ...cartAsPreviewRequest, couponCode })` — the cart already holds everything that request needs (dates, catalogue items, ad-hoc items). Render `totalRent`, `discountAmount`, `totalDeposit`, `grandTotal` **from the response**, not from local math.

Three things this buys:
- The frontend **never reimplements the percent/floor rule.** The only number it holds is `discountAmount`, computed by `CouponDiscountResolver`. This kills the duplicated-math risk on the FE side entirely.
- It satisfies #67's "preview shows discount" AC through the real preview endpoint rather than a bespoke one.
- It incidentally eliminates any client/server divergence in `totalRent` on the one screen where money is confirmed. (Rejected alternatives: a dedicated `POST /api/checkout/coupon-preview` — extra surface doing the same validation twice; refactoring the whole page onto server-driven totals — correct long-term but far beyond #68's scope. Neither is blocked by this choice.)

**Error handling:** an invalid coupon makes `preview` return **400**. The UI catches it, reads `err.response.data.error`, renders it in an Ant `Alert type="error"`, and **keeps the locally-computed totals unchanged**. Matches the existing pattern in `CheckoutPage`'s `createMutation.onError`. The error clears on the next Apply attempt or on Remove.

### Checkout coupon input UX

Location: the **preview screen** (`screen === 'preview'`), inside the existing totals `Card` (currently the `Descriptions` block with Total Rent / Total Deposit / Grand Total). Not the browse screen — the coupon belongs with the money, and the preview screen is where the customer is quoted.

- Collapsed state: a `Have a coupon?` link/`Input` + `Apply` button.
- Applied state: `<Tag color="green">SAVE20</Tag>` with a `Remove` (×) affordance, plus a new `Descriptions.Item label="Discount (SAVE20)"` row rendering `−{formatCurrency(discountAmount)}` between Total Rent and Total Deposit.
- Grand Total recomputes from the server response. **Total Deposit must visibly not move** — that is the AC the owner will eyeball.
- Loading: `Apply` shows `loading` while the preview mutation is in flight.
- Error: `Alert` directly under the input; input retains the typed value so the staff member can correct a typo.
- Also surface it on the customer-selection screen's summary `Descriptions` (which currently duplicates the totals), and disable/warn if a coupon is applied there.

**The stale-coupon guard — the single most likely bug in #68.** Any cart mutation (`addItem`, `removeItem`, `updateQuantity`) must **clear the applied coupon** and force a re-apply, because the discount is a function of the subtotal. Implement it inside `useCart`'s mutators so it cannot be forgotten at a call site. Backstop: `createReceipt` recomputes server-side from the code, so a stale client number can never be *persisted* — but it could be *displayed*, and a customer quoted ₹150 off who is charged ₹120 off is a counter argument the shop does not want.

### Types and cart state

`frontend/src/types/receipt.ts`:
- `CheckoutPreviewRequest` += `couponCode?: string | null`
- `CheckoutRequest` += `couponCode?: string | null`
- `CheckoutPreview` += `couponCode: string | null`, `discountAmount: number`
- `Receipt` += `couponCode: string | null`, `discountAmount: number`
- `ReceiptSummary` += same two
- `Cart` += `couponCode?: string | null`, `discountAmount?: number` (persisted to `localStorage` under the existing `rental_cart_v2` key — both optional, so old carts deserialise fine without a key bump)

`frontend/src/hooks/useCart.ts` — add `applyCoupon(code: string, discountAmount: number)` and `removeCoupon()`; have `addItem`/`removeItem`/`updateQuantity` set `couponCode: null, discountAmount: 0` in the next cart state.

`CheckoutPage.buildRequest()` — include `couponCode: cart!.couponCode ?? null`.

### Receipt display

`ReceiptDetailPage.tsx`, the shared/public receipt view, and the `ReceiptsPage` list all show `grandTotal`; without the discount line the arithmetic looks wrong. Add a `Discount (CODE)` row rendering `−{formatCurrency(discountAmount)}`, conditional on `couponCode !== null`. Use `formatCurrency` from `src/utils/currency.ts` — no manual `₹` concatenation.

### Coupon management page

**New dedicated page**, not a card in `SettingsPage`. `SettingsPage.tsx` is already ~350 lines carrying two unrelated concerns (late fee rules + user management); coupons are a third, with a growing list, a create/edit modal, and filtering. Adding it there repeats the mistake.

- File: `frontend/src/pages/coupons/CouponsPage.tsx`
- Route in `frontend/src/components/layout/AppLayout.tsx`:
  `<Route path="/coupons" element={<OwnerRoute><CouponsPage /></OwnerRoute>} />`
  — exactly the `/gallery/manage` precedent, using the `OwnerRoute` guard already defined in that file.
- Nav: add a `Coupons` entry to `TopNav` in `components/layout/Sidebar.tsx`, gated on `useAuth().isOwner`.
- Layout: `PageHeader label="Settings" title="Coupon" accent="Codes"` (matching `SettingsPage`'s `PageHeader` usage), an Ant `Table`, a `Create Coupon` button opening a `Modal` + `Form`, and a row-level `Edit` + `Popconfirm`-wrapped `Deactivate`/`Activate` toggle — all patterns already used in `SettingsPage`'s user-management card.
- Columns: Code (`<Text strong>`), Type (`<Tag>` PERCENT/FIXED), Value (`20%` / `formatCurrency(200)`), Min subtotal (`—` when null), Validity (`DD MMM – DD MMM`), Usage (`3 / 10` or `3 / ∞`), Status (`<Tag color={isActive ? 'green' : 'default'}>`), Actions.
- Form: `code` disabled on edit (the API omits it from `UpdateCouponRequest`); `value` bounded by an `InputNumber` with `max={100}` when type is PERCENT; `validFrom`/`validTo` via `DatePicker.RangePicker showTime`.
- Data: TanStack Query `['coupons']` + mutations with `queryClient.invalidateQueries`, mirroring `SettingsPage`. Server errors surfaced via `message.error(err.response?.data?.error ?? '…')` — the established pattern.

**Timezone trap:** a coupon "valid to 30 Sep" must mean end-of-day IST, not midnight. The form must send `validTo` as `23:59:59` IST of the chosen date (use `.endOf('day')` on the dayjs value, then `toApiDatetime` from `src/utils/datetime.ts`). Easy to get wrong; flagged in §8.

### API client and types placement

**New files: `frontend/src/api/coupons.ts` and `frontend/src/types/coupons.ts`** — not appended to `config.ts`/`types/config.ts`.

Justification: the URL prefix (`/config/coupons`) is an API-routing detail, not a client-module boundary. CLAUDE.md's rule is "one file per domain"; coupons are a distinct domain with ~5 types and 5 calls, and folding them into `config.ts` (currently 2 functions about late fee rules) reproduces the grab-bag problem. **This requires a one-line update to CLAUDE.md's `src/api/` and `src/types/` listings** — call it out in the PR.

```ts
// api/coupons.ts — object-export style, matching receipts.ts / reports.ts (the majority pattern)
export const couponsApi = {
  list:      (includeInactive?: boolean) => Promise<Coupon[]>,
  get:       (id: string)                => Promise<Coupon>,
  create:    (data: CreateCouponRequest) => Promise<Coupon>,
  update:    (id: string, data: UpdateCouponRequest) => Promise<Coupon>,
  setStatus: (id: string, isActive: boolean) => Promise<Coupon>,
}
```
```ts
// types/coupons.ts
export type DiscountType = 'PERCENT' | 'FIXED'
export interface Coupon { id: string; code: string; discountType: DiscountType; value: number;
  minSubtotal: number | null; validFrom: string; validTo: string;
  usageLimit: number | null; timesUsed: number; isActive: boolean;
  createdAt: string; updatedAt: string }
export interface CreateCouponRequest { code: string; discountType: DiscountType; value: number;
  minSubtotal: number | null; validFrom: string; validTo: string; usageLimit: number | null }
export type UpdateCouponRequest = Omit<CreateCouponRequest, 'code'>
export interface SetCouponStatusRequest { isActive: boolean }
```
All functions destructure `res.data.data` from the `ApiResponse<T>` envelope via the shared `client` — the established pattern.

---

## 7. Test plan

CLAUDE.md: *"Critical paths get 100% coverage: billing calculations, availability guards, transaction logic."* The discount calculation is a billing calculation and the `timesUsed` claim is transaction logic. Both get exhaustive coverage. Names are specifications (`should_…`).

### Unit — `CouponDiscountResolverTest` (JUnit 5 + Mockito, mocked `CouponRepository`, fixed `Clock`)
Exhaustive over the validation table and the arithmetic:
- `should_return_no_discount_when_code_is_null` / `..._is_blank` / `..._is_whitespace_only`
- `should_match_code_after_trimming_and_uppercasing`
- `should_reject_when_code_is_not_found`
- `should_reject_when_coupon_is_inactive`
- `should_reject_when_now_is_before_valid_from`
- `should_reject_when_now_is_after_valid_to`
- `should_accept_when_now_is_exactly_valid_from` *(boundary)*
- `should_reject_when_times_used_has_reached_usage_limit`
- `should_accept_when_usage_limit_is_null`
- `should_reject_when_subtotal_is_below_min_subtotal`
- `should_accept_when_subtotal_equals_min_subtotal` *(boundary)*
- `should_report_the_first_failing_condition_when_several_fail` *(pins the message order)*

`computeDiscountAmount` (pure, called directly):
- `should_floor_percent_discount_to_whole_rupee` — 10% of ₹1505 → ₹150
- `should_yield_zero_when_percent_discount_floors_below_one_rupee` — 1% of ₹50 → ₹0
- `should_apply_full_subtotal_for_a_hundred_percent_coupon`
- `should_cap_fixed_discount_at_the_discountable_subtotal` — ₹500 FIXED on ₹300 → ₹300
- `should_never_return_a_negative_discount`
- `should_return_zero_when_the_discountable_subtotal_is_zero`
- `should_include_the_sale_subtotal_in_the_base` *(guards the #57 seam: `new DiscountableSubtotals(100, 50)` → base 150; proves the extension point is already live)*

### Unit — `CheckoutServiceTest` additions
- `should_exclude_the_deposit_from_the_discountable_subtotal` ← **headline AC**
- `should_include_ad_hoc_line_rent_in_the_discountable_subtotal`
- `should_set_grand_total_to_rent_minus_discount_plus_deposit`
- `should_snapshot_coupon_code_and_discount_amount_on_the_receipt`
- `should_persist_zero_discount_and_null_coupon_code_when_no_code_is_supplied`
- `should_not_increment_times_used_during_preview`
- `should_increment_times_used_exactly_once_on_receipt_creation`
- `should_reject_checkout_when_the_usage_claim_returns_zero_rows`
- `should_produce_identical_totals_from_preview_and_create_for_the_same_cart` ← **the anti-duplication test.** Directly guards the "preview and create drift apart" risk.

### Unit — `CouponServiceTest`
- `should_reject_percent_value_above_one_hundred` / `..._below_one`
- `should_reject_non_positive_value`
- `should_reject_valid_to_not_after_valid_from`
- `should_reject_duplicate_code_ignoring_case_and_whitespace`
- `should_normalise_code_to_uppercase_on_create`
- `should_allow_lowering_usage_limit_below_times_used`
- `should_not_expose_times_used_on_the_update_request` *(compile-enforced; assert the response value is unchanged after an update)*

### Integration (Testcontainers, `src/integrationTest`, `*IT.java`, extends `AbstractIntegrationTest`)
- **`CouponUsageLimitConcurrencyIT` — the highest-value test in this epic.** Two concurrent `createReceipt` calls against a coupon with `usage_limit = 1`: exactly one receipt is created, the other gets the usage-limit `ConflictException`, and `times_used` reads exactly `1` afterwards. This is the only test that actually proves the conditional-`UPDATE` guard; a mocked unit test cannot.
- `CouponMigrationIT` — migration applies cleanly; inserting a duplicate `code` violates `ux_coupons_code` (#65 AC).
- `CouponCheckoutIT` — full checkout with a valid coupon persists `coupon_code`, `discount_amount`, and a `grand_total` satisfying the CHECK constraint; **`total_deposit` is byte-identical to the no-coupon run.**
- `CouponAdminSecurityIT` — EXECUTIVE receives 403 on GET/POST/PUT/PATCH `/api/config/coupons` (#66 AC); OWNER receives 2xx.
- `DiscountsGivenReportIT` — sums correctly over an IST range and excludes receipts just outside both boundaries.
- `CouponAdminDoesNotClobberTimesUsedIT` — load a coupon in the admin path, increment `times_used` out of band, then PUT; assert `times_used` survives. Proves `@DynamicUpdate` is doing its job.

### Frontend (Vitest + RTL + msw)
- `CheckoutPage` — applying a valid code renders the discount row and the server's grand total; **the deposit row is unchanged**; an invalid code renders the error `Alert` and leaves totals untouched; Remove clears the discount; changing a line quantity clears an applied coupon.
- `CouponsPage` — renders the list; the create form blocks a PERCENT value above 100 client-side; the deactivate toggle calls `setStatus`.
- `ReceiptDetailPage` — renders the discount row only when `couponCode` is non-null.

### E2E (Playwright, one flow)
Add to the existing checkout spec: create cart → add item → apply a valid coupon → confirm the discount and unchanged deposit → create receipt → the receipt page shows the coupon code and discount. Keep it to one flow per CLAUDE.md's "5–10 core flows" budget.

---

## 8. Risks and open questions

**Carried forward from the epic (unresolved, not blocking):**

1. **Stacking multiple coupons — assumed no.** Enforced structurally: `couponCode` is a scalar on both requests, and `receipts.coupon_code` is a single column. **Be aware this is not a cheap reversal** — stacking would need a `receipt_coupons` join table plus a per-coupon discount breakdown and an ordering rule (percent-then-fixed vs fixed-then-percent gives different answers). That is a rework, not an extension. Worth getting a durable "never" from the owner rather than a "not now".
2. **Per-customer usage limits — not modelled.** Good news: this *is* a clean later addition with **no schema change**. `SELECT COUNT(*) FROM receipts WHERE coupon_code = ? AND customer_id = ?` already answers "has this customer used it", because the receipt snapshots the code and already has `customer_id`. Adding a `per_customer_limit` column plus one check in `CouponDiscountResolver` would cover it.

**Found during this analysis:**

3. **Replace-all CRUD deviation from #66's literal text.** Decided against, justified in §3 (lost update on `times_used`, `deactivateAll()` as data loss, unbounded payload). Needs the tech-lead persona's explicit sign-off since the issue says otherwise.
4. **Ad-hoc items count toward the discountable subtotal.** Recommended yes (§4). The counter-argument — ad-hoc prices are already discretionary, so a coupon double-discounts — is a real business point, but the owner applies both and the alternative breaks the grand-total identity.
5. **Concurrent checkout race on `usageLimit`.** Guarded by the conditional UPDATE + Postgres READ COMMITTED predicate re-evaluation; proven by `CouponUsageLimitConcurrencyIT`. Considered closed, but it is the item most worth a reviewer's scrutiny.
6. **Duplicate submit on `POST /api/receipts`.** No idempotency key exists today; a double-tap already creates two receipts and double-books inventory. The coupon makes the consequence more visible (two redemptions). **Pre-existing, not introduced here.** Current mitigation is the frontend `isPending` button guard. Candidate for its own issue.
7. **`netFlow` in the daily/monthly revenue reports changes meaning** once discounts exist (§5). Business-visible: the owner's existing numbers will shift. Needs explicit acknowledgement before merge.
8. **`Invoice.totalRent` gross vs net** (§4). Recommended net. It feeds no arithmetic, so the change is display-only and safe, but it is a semantic change to an existing column.
9. **`@DynamicUpdate` on `Coupon`** — non-obvious JPA correctness detail. Without it, JPA's default full-row UPDATE makes the admin path capable of clobbering `times_used` despite `timesUsed` never appearing in a request DTO. Has its own integration test.
10. **`appliesTo` column omitted now.** YAGNI call (§1). Reversible with one additive `ALTER ... DEFAULT 'RENT_AND_SALE'`. A reviewer may prefer it up front; the cost of being wrong is that one line.
11. **Coupon validity timezone.** `validTo` must be end-of-day IST, not midnight, or a "valid through 30 Sep" coupon silently dies at 00:00 on the 30th. Frontend responsibility (`.endOf('day')`); worth a test.
12. **Checkout-screen total divergence.** `cartPricing.lineRentOf` (client) and `CheckoutService` (server) compute `totalRent` independently today. Once the coupon preview call returns server totals, any pre-existing divergence surfaces as a "wrong-looking discount". Mitigation: render the server's `totalRent`/`totalDeposit`/`grandTotal` after a successful apply, not the local ones. This is a latent bug this epic *exposes* rather than creates.
13. **A valid coupon can yield a ₹0 discount** (1% of a small subtotal). Intentional, allowed, and the reason `receipts_discount_requires_coupon_check` is one-directional. Will be reported as a bug unless documented for staff.
14. **`receipts_grand_total_check`** constrains future work — #57 must ALTER it to include `total_sale`. Feature, not bug, but name it now.

---

## 9. Suggested build / PR sequencing

One PR per sub-issue, with one split:

| PR | Issue | Depends on | Notes |
|---|---|---|---|
| 1 | **#65** — migration + `Coupon` entity + `CouponRepository` + `Receipt` columns | — | Blocks everything. Keep it separate even though the entity is unusable alone: a migration PR that can be reverted independently is worth the extra round-trip, and it is a small, high-scrutiny review. |
| 2 | **#66** — admin CRUD backend | 1 | Independent of #67. |
| 3 | **#67** — checkout integration + `CouponDiscountResolver` + `Invoice.totalRent` | 1 only | **Can be built in parallel with #66** by a second dev; merge after 1. The riskiest PR — deserves its own review, do not combine it with anything. |
| 4a | **#68a** — OWNER coupon management page + `api/coupons.ts` + `types/coupons.ts` + route/nav | 2 | Disjoint files from 4b. |
| 4b | **#68b** — checkout coupon input + cart state + receipt display | 3 | Disjoint files from 4a. |
| 5 | **#69** — discounts-given report + `netFlow` correction + `ReportsPage` | 1 (compiles), 3 (meaningful data) | Ship last. |

**Rationale for the #68 split:** a single frontend PR covering both the management page and the checkout flow would be large (new page + table + modal + form, plus checkout state changes, plus receipt display in three places) and would violate CLAUDE.md's "if a review takes more than an hour, the PR is too large". 4a and 4b touch genuinely disjoint files and unblock on different backend PRs, so they can land in either order.

**Combination candidates considered and rejected:** #65+#66 (tempting — the entity is useless without a way to create one — but bundles a migration with application logic in one revert unit); #65+#67 (no — #67 is the risky one).

**Critical-path ordering:** 1 → (2 ∥ 3) → (4a ∥ 4b) → 5. Two developers can work from PR 2 onward.

---

## 10. Revisions from persona review (Pass 1)

Reviewed by devils-advocate, tech-lead, and business-lead against this plan. Verdicts: devils-advocate REVISE, tech-lead REVISE, business-lead APPROVE. Deposit isolation, money/rounding math, security wiring, and the `LateFeeRule` replace-all deviation were all independently verified as sound and need no change. The following fixes are adopted:

### Owner decisions (confirmed)
- **No coupon stacking is permanent policy**, not a placeholder. The scalar `couponCode` column on `Receipt` (§2) stands as designed — no join-table hedge.
- **`netFlow`/`Invoice.totalRent` corrections (§4, §5) stay bundled** into #67/#69 as planned — they are necessary consequences of the discount, not scope creep. Call them out explicitly in each PR description for review, per §8 risk 6/7 — that step is now mandatory, not optional.
- **Ad-hoc line items count toward the discountable subtotal** — confirmed as designed in §4. No carve-out logic needed.

### Fixes required before/during build

1. **Customer-confirmation screen (§6).** The `screen === 'customer'` branch in `CheckoutPage.tsx` currently recomputes totals independently of the preview screen and was missed by the original coupon-input spec. It is the last screen before staff clicks confirm. Fix: render the same `Discount (CODE)` row and discounted grand total there, sourced from `cart.discountAmount` (already in cart state) — not fresh local math. Add this explicitly to §6's UX spec.

2. **Stale coupon on cart rehydration (§6).** The "stale-coupon guard" enumerated in §6 covers `addItem`/`removeItem`/`updateQuantity` but misses cart load from `localStorage`. Fix: `useCart`'s `loadCart()` must drop `couponCode`/`discountAmount` on rehydration (force re-apply), as a fourth trigger alongside the three cart mutators.

3. **Usage-limit race — WHERE clause gap (§4).** `incrementTimesUsed`'s conditional UPDATE only re-checks `usageLimit`, not `isActive`. A coupon deactivated mid-checkout could still be claimed. Fix: add `AND c.isActive = true` to the query:
   ```java
   @Query("UPDATE Coupon c SET c.timesUsed = c.timesUsed + 1 "
        + "WHERE c.id = :id AND c.isActive = true "
        + "AND (c.usageLimit IS NULL OR c.timesUsed < c.usageLimit)")
   ```
   Update §4's claim that this race is "closed" — it's closed once this clause ships, not before.

4. **`CouponUsageLimitConcurrencyIT` must force the race (§7).** As originally specified it could pass against a broken (unconditional) implementation if the two threads don't genuinely overlap. Fix: hold both threads on a `CountDownLatch` released only after both have called `resolve()`, then assert exactly one receipt was created and `times_used == 1`.

5. **`receipts_grand_total_check` migration risk (§2).** This CHECK validates immediately against all existing rows; a single violating historical row fails the whole Flyway migration and blocks app startup. Before merging #65: run `SELECT count(*) FROM receipts WHERE grand_total <> total_rent + total_deposit` against the production database and confirm zero rows. If that can't be done pre-merge, add the constraint `NOT VALID` and `VALIDATE CONSTRAINT` as a separate follow-up step instead of an atomic add.

6. **DTO enum leak (§3).** `CreateCouponRequest`/`UpdateCouponRequest` should take `String discountType` (validated via `Enum.valueOf` in `CouponService`), not `Coupon.DiscountType` directly — consistent with `CouponResponse`'s `String discountType` and with this module's existing convention of never leaking entity types into `configuration/model/` DTOs.

7. **#66 scope deviation needs to be on record before coding.** Post a comment on issue #66 documenting the per-entity-REST-over-replace-all decision (§3's reasoning: lost-update risk on `timesUsed`, `deactivateAll()` as data loss, unbounded payload growth) before starting that PR, so the deviation from the issue's literal text is visible pre-implementation, not disclosed for the first time in the PR body.

### Good-to-have, included in scope
- `@Size(max = 32)` on `couponCode` in `CheckoutRequest`/`CheckoutPreviewRequest`.
- Frontend confirmation modal when an applied discount would exceed ~90% of the subtotal (guards a typo'd FIXED coupon value; no backend hard cap, since a legitimately large package subtotal shouldn't be artificially capped).
- Normalize `validTo` to end-of-day IST server-side in `CouponService` as well as client-side, for defense in depth against non-browser clients (Swagger, future integrations).
- Add `should_allow_executive_to_apply_coupon_at_checkout` to the integration test suite (§7) — today only the OWNER-admin-blocked case is tested; the checkout-apply path being open to EXECUTIVE is implied by `SecurityConfig` but unverified.
- Derive minimum-subtotal rejection wording from the `DiscountableSubtotals` fields rather than hardcoding "rent subtotal" (keeps the message correct once #57 adds a sale component).
- Pin `claimCouponUsage`'s call site to run before `ReceiptNumberService.generateReceiptNumber()` in `createReceipt()`, for a cleaner concurrency-test interleaving.

### Accepted as-is, no action
- Report date-range boundary wording ("exclusive" comment vs. actually-inclusive `BETWEEN`) — pre-existing pattern across all `ReportingService` methods, not introduced by this epic.
- Coupon code visible via the public share-token receipt link — it's the customer's own receipt.
- Per-condition rejection message specificity as a minor enumeration surface — staff-only, authenticated, single-shop scale.
- Migration's `id`/`created_at`/`updated_at` columns without SQL-level `DEFAULT`s (diverges from `V1__initial_schema.sql`'s style) — harmless since Hibernate always supplies them on the only write path.

---

## 11. Pass 2 refinements

Pass 2 verdicts: devils-advocate REVISE (5 blockers — all second-order defects introduced by the §10 fixes themselves, none requiring a design change), tech-lead APPROVE (all 3 original blockers confirmed resolved), business-lead APPROVE (all 4 suggestions confirmed closed). The following five fixes close the devils-advocate's Pass 2 findings; the plan is build-ready once these are applied.

1. **Customer-confirmation screen must not recompute independently of the preview screen (refines §10 fix 1).** Sourcing only `discountAmount` from the cart while still locally recomputing `totalRent`/`totalDeposit` reintroduces the divergence the fix was meant to close — if an item's rate changes between the preview call and the confirmation screen, the two screens show different totals. Fix: when a coupon is applied, store the **entire** preview response (`totalRent`, `totalDeposit`, `discountAmount`, `grandTotal`) in cart/component state and render both the preview screen and the customer-confirmation screen from that single stored result — no independent recomputation on either screen once a coupon is applied.

2. **`claimCouponUsage`'s zero-row result is ambiguous after `AND c.isActive = true` (refines §10 fix 3).** A `claimed == 0` result now means *either* the usage limit was reached *or* the coupon was deactivated mid-checkout — but the code still unconditionally throws "has reached its usage limit," which is a false message when the real cause was deactivation. Fix: on `claimed == 0`, re-read the coupon row and branch the message — `"...is no longer active."` if `!isActive`, `"...has reached its usage limit."` otherwise, `"...is not valid."` if the row is now gone.

3. **The `String discountType` + `Enum.valueOf` fix (§10 fix 6) needs explicit exception handling, and its stated rationale is wrong.** `Enum.valueOf` throws an unhandled `IllegalArgumentException` for a bad value, which `GlobalExceptionHandler`'s catch-all turns into a 500 with no field-level detail — worse than the entity-typed field it replaced. Fix: wrap the conversion and rethrow as `ValidationException("Discount type must be PERCENT or FIXED.")` → 400. Also correct the record: this codebase does **not** have a blanket "never leak entity types into DTOs" convention — `inventory/model/request/CreateItemRequest.java` takes `Item.Category`/`Item.ItemType` directly. The real justification for `String` here is the explicit 400-vs-500 handling, not a convention that doesn't exist — say so in the PR description.

4. **`CouponUsageLimitConcurrencyIT`'s `CountDownLatch` placement must be pinned precisely, or it can deadlock CI (refines §10 fix 4).** `AbstractIntegrationTest` uses a **singleton Testcontainers instance shared across the whole integration suite** — if the latch is placed after the conditional UPDATE acquires its row lock, the second thread blocks on the lock and never reaches the latch, hanging that test *and* wedging every other integration test sharing the container. Fix, specify all three: (a) the barrier must sit inside the transaction, before the UPDATE fires — e.g. a test-only hook or `@SpyBean` on `CouponDiscountResolver` that counts down and awaits immediately after `resolve()` returns, before `claimCouponUsage` runs; (b) every `await()` must carry a timeout so a mis-wired barrier fails the test loudly instead of hanging the suite; (c) the fixture must request item quantity ≥ 2 — with quantity 1, the availability guard (checked before the coupon claim) rejects the second thread first, and the test would pass while proving nothing about the usage-limit guard. Assert the losing thread's exception message is specifically the usage-limit rejection, not an availability rejection.

5. **Server-side `validTo` end-of-day normalization (§10 good-to-have) contradicts §6's `DatePicker.RangePicker showTime` spec.** As written, an owner who deliberately picks a specific time via `showTime` would have it silently overwritten to 23:59:59 server-side, and the coupons table would then display a time the owner never set. Pick one, not both: **drop `showTime`** from the create/edit form (coupons are date-granularity — "valid through 30 Sep" — matching how the owner actually thinks about a promotion window) and keep the server-side end-of-day normalization as the single source of truth for both browser and non-browser clients (Swagger, future integrations).

### Noted, not required to fix (devils-advocate Pass 2 suggestions, accepted as low-risk for a single-shop context)
- Apply-coupon error handling should route item-level errors (inactive/deleted item in the cart) to a page-level alert, not the coupon-input alert, so staff aren't misled about the cause — worth doing when building §6, not a blocker.
- Duplicate-coupon-code race returns 500 instead of the 409 §3 describes — accepted as-is; a single-operator admin UI makes concurrent duplicate-code submission effectively impossible.
- Match `LateFeeRule`'s boxed `Boolean isActive` + `getIsActive()` shape on `Coupon` instead of primitive `boolean`, for consistency within the same package — minor, apply during implementation.
- `Cart.discountAmount` is optional in the type — the confirmation-screen fix (item 1 above) must treat `undefined` as 0 and suppress the discount row rather than rendering `−₹NaN`.

---

### Critical files for implementation

- `backend/src/main/java/com/fashionrental/receipt/CheckoutService.java` — `preview()` and `createReceipt()` both need the resolver call; `discountableSubtotals()` is the #57 seam
- `backend/src/main/java/com/fashionrental/receipt/Receipt.java` — `couponCode` + `discountAmount` snapshot columns, and the `grandTotal` identity
- `backend/src/main/java/com/fashionrental/configuration/ConfigService.java` — the replace-all pattern being deliberately *not* copied; `CouponService` mirrors its layering only
- `backend/src/main/resources/db/migration/V20260917001__add_coupons_and_receipt_discount.sql` — new; #65's entire AC
- `frontend/src/pages/checkout/CheckoutPage.tsx` — computes totals client-side today and never calls `receiptsApi.preview`; this is the file the coupon UX decision hinges on
