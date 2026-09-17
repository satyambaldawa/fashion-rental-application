# Ad-Hoc Line Items Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let staff bill a product that is not in the inventory catalogue by typing its name, size, price, deposit and quantity directly into checkout — producing a normal, returnable Receipt.

**Architecture:** Each typed-in product becomes a hidden `items` row flagged `is_ad_hoc = true`, so every downstream flow (return, invoice, customer history, public share pages) keeps working against a real foreign key with zero changes. Exactly one query — the item browse `Specification` — filters the flag out. On the frontend, `CartItem` becomes a discriminated union so the compiler forces every pricing site to handle flat-priced lines, and `/quick-rental` mounts the existing `CheckoutPage` with a type-first entry screen.

**Tech Stack:** Java 21, Spring Boot, Gradle (Kotlin DSL), JPA/Hibernate (`ddl-auto: validate`), Flyway, PostgreSQL 16, JUnit 5 + Mockito + AssertJ, Testcontainers; React 18 + TypeScript, Vite, Ant Design, Zustand, TanStack Query, Vitest + React Testing Library + MSW, pnpm.

**Spec:** `docs/superpowers/specs/2026-09-15-ad-hoc-line-items-design.md`

## Global Constraints

- **Money is `INTEGER` in SQL and `int` in Java — whole rupees only.** Never `float`, `double`, or `DECIMAL`. No paise conversion anywhere. Frontend displays via `formatCurrency(amount)` from `src/utils/currency.ts` — no multiply/divide by 100.
- **Datetimes are `TIMESTAMPTZ` / `OffsetDateTime`, ISO 8601, IST.**
- **Every endpoint returns `ApiResponse<T>`** — `{ success, data, error }`. Never a raw object, `Map`, or primitive.
- **Every `@RequestBody` is a typed record from the module's `model/request/` package, annotated `@Valid`**, with Bean Validation annotations on every field.
- **Flyway naming:** `V<YYYYMMDD><NNN>__<description>.sql`. Hibernate runs `ddl-auto: validate` — entity and migration must land in the same commit or the app refuses to start.
- **Frontend types live in `src/types/<domain>.ts`** — never inline shapes in API functions or components.
- **Build tool is Gradle, never Maven.** Backend: `./gradlew test`, `./gradlew integrationTest`. Frontend: `pnpm test`, `pnpm type-check`, `pnpm lint`.
- **Test names are specifications:** `should_calculate_late_fee_when_returned_3_hours_late`, not `testLateFee`.
- **Never commit secrets.** This is a public repository.
- **Ask before any git push or PR.** Commits within this plan are expected; pushing is not.

---

## File Structure

**Backend — create**
| File | Responsibility |
|---|---|
| `backend/src/main/resources/db/migration/V20260915001__add_is_ad_hoc_to_items.sql` | Adds the `is_ad_hoc` column |
| `backend/src/main/java/com/fashionrental/receipt/model/request/AdHocLineItem.java` | Validated request DTO for one typed-in product |
| `backend/src/integrationTest/java/com/fashionrental/receipt/AdHocCheckoutIT.java` | Non-transactional IT proving real rollback |

**Backend — modify**
| File | Change |
|---|---|
| `inventory/Item.java` | `isAdHoc` field + accessors |
| `inventory/ItemService.java:57` | One predicate excluding ad-hoc rows from browse |
| `receipt/model/request/CheckoutRequest.java` | `adHocItems` list; relax `@NotEmpty`; `@AssertTrue` |
| `receipt/model/request/CheckoutPreviewRequest.java` | Same |
| `receipt/model/response/PreviewLineItem.java` | `itemId` becomes nullable |
| `receipt/CheckoutService.java` | Ad-hoc item creation + flat pricing, in `preview` and `createReceipt` |

**Frontend — create**
| File | Responsibility |
|---|---|
| `src/pages/checkout/AdHocItemModal.tsx` | The typed-product entry form |
| `src/pages/checkout/AdHocEntryScreen.tsx` | Type-first landing screen for `/quick-rental` |
| `src/pages/checkout/cartPricing.ts` | `lineRentOf` / `perDayRateOf` — the only place pricing branches on cart-item kind |

**Frontend — modify**
| File | Change |
|---|---|
| `src/types/receipt.ts` | `CartItem` union, `AdHocLineItem`, nullable `PreviewLineItem.itemId` |
| `src/hooks/useCart.ts` | Key on `lineKey`; storage key `rental_cart_v2` |
| `src/pages/checkout/CheckoutPage.tsx` | Pricing-aware totals/columns, `adhoc` screen, request partition |
| `src/components/layout/AppLayout.tsx` | `/quick-rental` route |
| `src/components/layout/Sidebar.tsx` | `Quick Rental` nav item |

---

## Task 1: `is_ad_hoc` column, entity field, and browse filter

Delivers the schema change and the single containment point. Nothing typed-in exists yet — this task only guarantees that when ad-hoc rows *do* exist, they never appear in item browse or the Inventory page.

**Files:**
- Create: `backend/src/main/resources/db/migration/V20260915001__add_is_ad_hoc_to_items.sql`
- Modify: `backend/src/main/java/com/fashionrental/inventory/Item.java`
- Modify: `backend/src/main/java/com/fashionrental/inventory/ItemService.java:55-71`
- Test: `backend/src/test/java/com/fashionrental/inventory/ItemServiceTest.java`

**Interfaces:**
- Consumes: nothing (first task)
- Produces: `Item.getIsAdHoc()` / `Item.setIsAdHoc(Boolean)`; DB column `items.is_ad_hoc BOOLEAN NOT NULL DEFAULT FALSE`

- [ ] **Step 1: Write the failing test**

Add to `ItemServiceTest`. The existing tests capture the `Specification` passed to `itemRepository.findAll` — this one asserts the ad-hoc predicate is built by checking the service applies it to the criteria root.

```java
    @Test
    void should_exclude_ad_hoc_items_from_item_list() {
        ArgumentCaptor<Specification<Item>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        when(itemRepository.findAll(specCaptor.capture(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(availabilityService.batchGetAvailableQuantities(any(), any(), any()))
                .thenReturn(Map.of());

        itemService.listItems(null, null, null, null, 0, 20, null, null);

        var root = org.mockito.Mockito.mock(jakarta.persistence.criteria.Root.class);
        var query = org.mockito.Mockito.mock(jakarta.persistence.criteria.CriteriaQuery.class);
        var cb = org.mockito.Mockito.mock(jakarta.persistence.criteria.CriteriaBuilder.class);
        var path = org.mockito.Mockito.mock(jakarta.persistence.criteria.Path.class);
        when(root.get("isAdHoc")).thenReturn(path);

        specCaptor.getValue().toPredicate(root, query, cb);

        verify(cb).isFalse(path);
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests com.fashionrental.inventory.ItemServiceTest.should_exclude_ad_hoc_items_from_item_list`
Expected: FAIL — `Wanted but not invoked: cb.isFalse(path)`, because the specification never touches `isAdHoc`.

- [ ] **Step 3: Write the migration**

Create `backend/src/main/resources/db/migration/V20260915001__add_is_ad_hoc_to_items.sql`:

```sql
-- Ad-hoc items are products typed in at checkout for a single rental. They are real
-- rentable rows so that receipts, returns and invoices keep a non-null item_id, but
-- they are excluded from the browsable catalogue (see ItemService#listItems).
ALTER TABLE items ADD COLUMN is_ad_hoc BOOLEAN NOT NULL DEFAULT FALSE;
```

No index: the browse query filters `is_ad_hoc = false`, which matches nearly every row, so the planner would never choose one.

- [ ] **Step 4: Add the entity field**

In `Item.java`, after the `isActive` field:

```java
    @Column(name = "is_ad_hoc", nullable = false)
    private Boolean isAdHoc = false;
```

And with the other accessors:

```java
    public Boolean getIsAdHoc() { return isAdHoc; }
    public void setIsAdHoc(Boolean isAdHoc) { this.isAdHoc = isAdHoc; }
```

- [ ] **Step 5: Add the browse filter**

In `ItemService.java`, inside the `Specification` lambda, directly after the existing `isActive` predicate at line 57:

```java
            predicates.add(cb.isTrue(root.get("isActive")));
            predicates.add(cb.isFalse(root.get("isAdHoc")));
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `cd backend && ./gradlew test --tests com.fashionrental.inventory.ItemServiceTest`
Expected: PASS — all tests in the class, including the new one.

- [ ] **Step 7: Verify the migration applies against a real database**

Run: `cd backend && ./gradlew integrationTest --tests com.fashionrental.ContextLoadsSmokeIT`
Expected: PASS. This boots Flyway and Hibernate `validate` against real PostgreSQL — it is the only thing that proves the entity field and the column actually agree. A green unit test does not.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/resources/db/migration/V20260915001__add_is_ad_hoc_to_items.sql \
        backend/src/main/java/com/fashionrental/inventory/Item.java \
        backend/src/main/java/com/fashionrental/inventory/ItemService.java \
        backend/src/test/java/com/fashionrental/inventory/ItemServiceTest.java
git commit -m "feat(inventory): add is_ad_hoc flag and exclude such items from browse"
```

---

## Task 2: `AdHocLineItem` DTO and request validation

Delivers the request contract. `items` can now be empty *provided* `adHocItems` is not — without the `@AssertTrue`, an entirely empty checkout request would validate.

**Files:**
- Create: `backend/src/main/java/com/fashionrental/receipt/model/request/AdHocLineItem.java`
- Modify: `backend/src/main/java/com/fashionrental/receipt/model/request/CheckoutRequest.java`
- Modify: `backend/src/main/java/com/fashionrental/receipt/model/request/CheckoutPreviewRequest.java`
- Test: `backend/src/test/java/com/fashionrental/receipt/model/request/CheckoutRequestValidationTest.java` (create)

**Interfaces:**
- Consumes: nothing from Task 1
- Produces: `AdHocLineItem(String name, String size, Integer flatPrice, Integer deposit, Integer quantity)`; `CheckoutRequest.adHocItems()`; `CheckoutPreviewRequest.adHocItems()`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/fashionrental/receipt/model/request/CheckoutRequestValidationTest.java`:

```java
package com.fashionrental.receipt.model.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutRequestValidationTest {

    private static Validator validator;

    private static final OffsetDateTime START = OffsetDateTime.parse("2026-04-21T10:00:00+05:30");
    private static final OffsetDateTime END = OffsetDateTime.parse("2026-04-24T10:00:00+05:30");

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void should_accept_request_with_only_ad_hoc_items() {
        CheckoutRequest request = new CheckoutRequest(
                UUID.randomUUID(), START, END, List.of(),
                List.of(new AdHocLineItem("Red Sherwani", "L", 500, 1000, 1)),
                null
        );

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void should_reject_request_when_both_item_lists_are_empty() {
        CheckoutRequest request = new CheckoutRequest(
                UUID.randomUUID(), START, END, List.of(), List.of(), null
        );

        assertThat(validator.validate(request))
                .extracting(v -> v.getMessage())
                .contains("A receipt needs at least one item");
    }

    @Test
    void should_reject_ad_hoc_item_with_blank_name() {
        CheckoutRequest request = new CheckoutRequest(
                UUID.randomUUID(), START, END, List.of(),
                List.of(new AdHocLineItem("  ", "L", 500, 1000, 1)),
                null
        );

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void should_reject_ad_hoc_item_priced_below_one_rupee() {
        CheckoutRequest request = new CheckoutRequest(
                UUID.randomUUID(), START, END, List.of(),
                List.of(new AdHocLineItem("Red Sherwani", "L", 0, 1000, 1)),
                null
        );

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void should_accept_ad_hoc_item_with_zero_deposit() {
        CheckoutRequest request = new CheckoutRequest(
                UUID.randomUUID(), START, END, List.of(),
                List.of(new AdHocLineItem("Red Sherwani", "L", 500, 0, 1)),
                null
        );

        assertThat(validator.validate(request)).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests com.fashionrental.receipt.model.request.CheckoutRequestValidationTest`
Expected: FAIL to **compile** — `AdHocLineItem` does not exist and `CheckoutRequest` has 5 components, not 6.

- [ ] **Step 3: Create the DTO**

Create `AdHocLineItem.java`:

```java
package com.fashionrental.receipt.model.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * One product typed in at checkout rather than chosen from the catalogue.
 *
 * <p>{@code flatPrice} is the total rent for the WHOLE rental period, per unit — not a
 * per-day rate like {@code Item.rate}. See §3.5 of the design spec.
 */
public record AdHocLineItem(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String size,
        @NotNull @Min(1) Integer flatPrice,
        @NotNull @Min(0) Integer deposit,
        @NotNull @Min(1) Integer quantity
) {}
```

- [ ] **Step 4: Update `CheckoutRequest`**

Replace the whole record body:

```java
package com.fashionrental.receipt.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CheckoutRequest(
        @NotNull UUID customerId,
        @NotNull OffsetDateTime startDatetime,
        @NotNull OffsetDateTime endDatetime,
        @Valid List<CheckoutLineItem> items,
        @Valid List<AdHocLineItem> adHocItems,
        String notes
) {
    public CheckoutRequest {
        items = items == null ? List.of() : items;
        adHocItems = adHocItems == null ? List.of() : adHocItems;
    }

    @AssertTrue(message = "A receipt needs at least one item")
    public boolean isAtLeastOneLinePresent() {
        return !items.isEmpty() || !adHocItems.isEmpty();
    }
}
```

The compact constructor normalising `null` to `List.of()` matters: JSON omitting either key would otherwise NPE inside `isAtLeastOneLinePresent()`.

- [ ] **Step 5: Update `CheckoutPreviewRequest` the same way**

```java
package com.fashionrental.receipt.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;

public record CheckoutPreviewRequest(
        @NotNull OffsetDateTime startDatetime,
        @NotNull OffsetDateTime endDatetime,
        @Valid List<CheckoutLineItem> items,
        @Valid List<AdHocLineItem> adHocItems
) {
    public CheckoutPreviewRequest {
        items = items == null ? List.of() : items;
        adHocItems = adHocItems == null ? List.of() : adHocItems;
    }

    @AssertTrue(message = "A preview needs at least one item")
    public boolean isAtLeastOneLinePresent() {
        return !items.isEmpty() || !adHocItems.isEmpty();
    }
}
```

- [ ] **Step 6: Fix existing call sites**

Both records gained a component, so every existing construction fails to compile. Find them:

```bash
cd backend && grep -rn "new CheckoutRequest(\|new CheckoutPreviewRequest(" src/
```

Add `List.of()` for `adHocItems` in each — it goes **after** `items` and **before** `notes` in `CheckoutRequest`, and last in `CheckoutPreviewRequest`. Expect hits in `CheckoutServiceTest`, `ReceiptControllerTest`, and `RentalFlowIT`.

- [ ] **Step 7: Run the tests to verify they pass**

Run: `cd backend && ./gradlew test`
Expected: PASS — the whole unit suite, confirming the call-site fixes are complete.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/fashionrental/receipt/model/request/ \
        backend/src/test/java/com/fashionrental/receipt/
git commit -m "feat(checkout): accept ad-hoc line items in checkout requests

Both item lists may now be empty individually, so an @AssertTrue guard
replaces the @NotEmpty that previously made an empty request impossible."
```

---

## Task 3: Ad-hoc pricing and item creation in `createReceipt`

The core of the feature. Note `lineRent = flatPrice * quantity` — **not** `rate * days * quantity`. That divergence is deliberate and safe; §3.5 of the spec records that nothing in the codebase recomputes that product.

**Files:**
- Modify: `backend/src/main/java/com/fashionrental/receipt/CheckoutService.java`
- Test: `backend/src/test/java/com/fashionrental/receipt/CheckoutServiceTest.java`

**Interfaces:**
- Consumes: `Item.setIsAdHoc(Boolean)` (Task 1); `AdHocLineItem`, `CheckoutRequest.adHocItems()` (Task 2)
- Produces: receipts whose line items may point at ad-hoc `Item` rows

- [ ] **Step 1: Write the failing tests**

Add to `CheckoutServiceTest`. The existing `makeItem` helper at line 381 stays as is.

```java
    // ─── Ad-hoc line items ───────────────────────────────────────────────────

    @Test
    void should_price_ad_hoc_line_as_flat_total_regardless_of_rental_days() {
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(new Customer()));
        when(dateTimeUtil.calculateRentalDays(START, END)).thenReturn(3);
        when(itemRepository.save(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));
        when(receiptNumberService.generateReceiptNumber()).thenReturn("R-001");
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(inv -> inv.getArgument(0));

        checkoutService.createReceipt(new CheckoutRequest(
                customerId, START, END, List.of(),
                List.of(new AdHocLineItem("Red Sherwani", "L", 500, 1000, 1)),
                null
        ));

        ArgumentCaptor<Receipt> receiptCaptor = ArgumentCaptor.forClass(Receipt.class);
        verify(receiptRepository).save(receiptCaptor.capture());
        Receipt saved = receiptCaptor.getValue();

        assertThat(saved.getLineItems()).hasSize(1);
        assertThat(saved.getLineItems().get(0).getLineRent()).isEqualTo(500);
        assertThat(saved.getTotalRent()).isEqualTo(500);
    }

    @Test
    void should_multiply_ad_hoc_flat_price_by_quantity() {
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(new Customer()));
        when(dateTimeUtil.calculateRentalDays(START, END)).thenReturn(3);
        when(itemRepository.save(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));
        when(receiptNumberService.generateReceiptNumber()).thenReturn("R-001");
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(inv -> inv.getArgument(0));

        checkoutService.createReceipt(new CheckoutRequest(
                customerId, START, END, List.of(),
                List.of(new AdHocLineItem("Red Sherwani", "L", 500, 1000, 2)),
                null
        ));

        ArgumentCaptor<Receipt> receiptCaptor = ArgumentCaptor.forClass(Receipt.class);
        verify(receiptRepository).save(receiptCaptor.capture());

        assertThat(receiptCaptor.getValue().getTotalRent()).isEqualTo(1000);
        assertThat(receiptCaptor.getValue().getTotalDeposit()).isEqualTo(2000);
    }

    @Test
    void should_derive_per_day_rate_snapshot_from_flat_price() {
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(new Customer()));
        when(dateTimeUtil.calculateRentalDays(START, END)).thenReturn(3);
        when(itemRepository.save(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));
        when(receiptNumberService.generateReceiptNumber()).thenReturn("R-001");
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(inv -> inv.getArgument(0));

        checkoutService.createReceipt(new CheckoutRequest(
                customerId, START, END, List.of(),
                List.of(new AdHocLineItem("Red Sherwani", "L", 500, 1000, 1)),
                null
        ));

        ArgumentCaptor<Receipt> receiptCaptor = ArgumentCaptor.forClass(Receipt.class);
        verify(receiptRepository).save(receiptCaptor.capture());

        // 500 / 3 = 166.67 → 167. This is the late-fee basis only; lineRent stays 500.
        assertThat(receiptCaptor.getValue().getLineItems().get(0).getRateSnapshot()).isEqualTo(167);
    }

    @Test
    void should_create_hidden_item_row_for_each_ad_hoc_entry() {
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(new Customer()));
        when(dateTimeUtil.calculateRentalDays(START, END)).thenReturn(3);
        when(itemRepository.save(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));
        when(receiptNumberService.generateReceiptNumber()).thenReturn("R-001");
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(inv -> inv.getArgument(0));

        checkoutService.createReceipt(new CheckoutRequest(
                customerId, START, END, List.of(),
                List.of(new AdHocLineItem("Red Sherwani", "L", 500, 1000, 2)),
                null
        ));

        ArgumentCaptor<Item> itemCaptor = ArgumentCaptor.forClass(Item.class);
        verify(itemRepository).save(itemCaptor.capture());
        Item created = itemCaptor.getValue();

        assertThat(created.getName()).isEqualTo("Red Sherwani");
        assertThat(created.getSize()).isEqualTo("L");
        assertThat(created.getIsAdHoc()).isTrue();
        assertThat(created.getIsActive()).isTrue();
        assertThat(created.getCategory()).isEqualTo(Item.Category.OTHER);
        assertThat(created.getItemType()).isEqualTo(Item.ItemType.INDIVIDUAL);
        assertThat(created.getQuantity()).isEqualTo(2);
        assertThat(created.getPurchaseRate()).isNull();
    }
```

Add the import `com.fashionrental.receipt.model.request.AdHocLineItem` at the top of the file.

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./gradlew test --tests com.fashionrental.receipt.CheckoutServiceTest`
Expected: FAIL — the new tests produce receipts with zero line items, because `createReceipt` ignores `adHocItems` entirely.

- [ ] **Step 3: Implement ad-hoc handling in `createReceipt`**

In `CheckoutService.createReceipt`, after the existing `for (var lineItemRequest : request.items())` loop and **before** `receipt.setTotalRent(totalRent);`:

```java
        for (AdHocLineItem adHoc : request.adHocItems()) {
            Item adHocItem = itemRepository.save(buildAdHocItem(adHoc, rentalDays));
            ReceiptLineItem line = buildAdHocLineItem(receipt, adHocItem, adHoc, rentalDays);
            lineItems.add(line);
            totalRent    += line.getLineRent();
            totalDeposit += line.getLineDeposit();
        }
```

- [ ] **Step 4: Add the two private builders**

Alongside the existing `buildLineItem` at line 178:

```java
    private Item buildAdHocItem(AdHocLineItem adHoc, int rentalDays) {
        Item item = new Item();
        item.setName(adHoc.name());
        item.setSize(adHoc.size());
        item.setCategory(Item.Category.OTHER);
        item.setItemType(Item.ItemType.INDIVIDUAL);
        item.setRate(derivePerDayRate(adHoc.flatPrice(), rentalDays));
        item.setDeposit(adHoc.deposit());
        item.setQuantity(adHoc.quantity());
        item.setIsActive(true);
        item.setIsAdHoc(true);
        return item;
    }

    /**
     * Builds the line for a typed-in product. Unlike {@link #buildLineItem}, lineRent is the
     * flat price the user typed multiplied by quantity — it is NOT derived from the per-day
     * rate, so rateSnapshot * rentalDays * quantity may differ from lineRent by a rupee or
     * two of rounding. Nothing recomputes that product; rateSnapshot exists only as the
     * late-fee basis (BillingService#calculateLateFee) and for display.
     */
    private ReceiptLineItem buildAdHocLineItem(Receipt receipt, Item item, AdHocLineItem adHoc, int rentalDays) {
        ReceiptLineItem li = new ReceiptLineItem();
        li.setReceipt(receipt);
        li.setItem(item);
        li.setQuantity(adHoc.quantity());
        li.setRateSnapshot(derivePerDayRate(adHoc.flatPrice(), rentalDays));
        li.setDepositSnapshot(adHoc.deposit());
        li.setLineRent(adHoc.flatPrice() * adHoc.quantity());
        li.setLineDeposit(adHoc.deposit() * adHoc.quantity());
        return li;
    }

    private int derivePerDayRate(int flatPrice, int rentalDays) {
        return (int) Math.round((double) flatPrice / rentalDays);
    }
```

Add the import `com.fashionrental.receipt.model.request.AdHocLineItem`.

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests com.fashionrental.receipt.CheckoutServiceTest`
Expected: PASS — all tests in the class.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/fashionrental/receipt/CheckoutService.java \
        backend/src/test/java/com/fashionrental/receipt/CheckoutServiceTest.java
git commit -m "feat(checkout): create hidden items and flat-priced lines for ad-hoc entries

The typed price is the total for the whole rental, so lineRent is the flat
price times quantity rather than rate x days x quantity. rateSnapshot holds
the derived per-day figure, which is what late fees are computed from."
```

---

## Task 4: Ad-hoc support in `preview`

Preview must persist **nothing** — no `items` rows may be created by a preview call.

**Files:**
- Modify: `backend/src/main/java/com/fashionrental/receipt/CheckoutService.java`
- Modify: `backend/src/main/java/com/fashionrental/receipt/model/response/PreviewLineItem.java`
- Test: `backend/src/test/java/com/fashionrental/receipt/CheckoutServiceTest.java`

**Interfaces:**
- Consumes: `AdHocLineItem`, `CheckoutPreviewRequest.adHocItems()` (Task 2); `derivePerDayRate` (Task 3)
- Produces: `PreviewLineItem` with nullable `itemId`

- [ ] **Step 1: Write the failing test**

```java
    @Test
    void should_preview_ad_hoc_line_without_persisting_an_item() {
        when(dateTimeUtil.calculateRentalDays(START, END)).thenReturn(3);

        CheckoutPreviewResponse preview = checkoutService.preview(new CheckoutPreviewRequest(
                START, END, List.of(),
                List.of(new AdHocLineItem("Red Sherwani", "L", 500, 1000, 2))
        ));

        assertThat(preview.lineItems()).hasSize(1);
        PreviewLineItem line = preview.lineItems().get(0);
        assertThat(line.itemId()).isNull();
        assertThat(line.itemName()).isEqualTo("Red Sherwani");
        assertThat(line.rate()).isEqualTo(167);
        assertThat(line.lineRent()).isEqualTo(1000);
        assertThat(line.lineDeposit()).isEqualTo(2000);
        assertThat(line.availableQuantity()).isEqualTo(2);
        assertThat(preview.allAvailable()).isTrue();
        assertThat(preview.totalRent()).isEqualTo(1000);

        verify(itemRepository, org.mockito.Mockito.never()).save(any(Item.class));
    }
```

Add the import `com.fashionrental.receipt.model.response.PreviewLineItem`.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests com.fashionrental.receipt.CheckoutServiceTest.should_preview_ad_hoc_line_without_persisting_an_item`
Expected: FAIL — `preview.lineItems()` is empty; `adHocItems` is ignored.

- [ ] **Step 3: Make `PreviewLineItem.itemId` nullable**

Records cannot express nullability in the type, so document it:

```java
package com.fashionrental.receipt.model.response;

import java.util.UUID;

public record PreviewLineItem(
        UUID itemId,          // null for ad-hoc lines — no items row exists until checkout
        String itemName,
        int rate,
        int deposit,
        int quantity,
        int rentalDays,
        int lineRent,
        int lineDeposit,
        int availableQuantity
) {}
```

- [ ] **Step 4: Add ad-hoc lines to `preview`**

In `CheckoutService.preview`, after the existing `for (var lineItem : request.items())` loop and **before** the `int totalRent = ...` reduction:

```java
        for (AdHocLineItem adHoc : request.adHocItems()) {
            int perDayRate = derivePerDayRate(adHoc.flatPrice(), rentalDays);
            lineItems.add(new PreviewLineItem(
                    null,
                    adHoc.name(),
                    perDayRate,
                    adHoc.deposit(),
                    adHoc.quantity(),
                    rentalDays,
                    adHoc.flatPrice() * adHoc.quantity(),
                    adHoc.deposit() * adHoc.quantity(),
                    adHoc.quantity()
            ));
        }
```

An ad-hoc product is always available by definition, so it never adds to `unavailableItems`.

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests com.fashionrental.receipt.CheckoutServiceTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/fashionrental/receipt/CheckoutService.java \
        backend/src/main/java/com/fashionrental/receipt/model/response/PreviewLineItem.java \
        backend/src/test/java/com/fashionrental/receipt/CheckoutServiceTest.java
git commit -m "feat(checkout): preview ad-hoc lines without persisting items"
```

---

## Task 5: Integration tests against real PostgreSQL

The two highest-value tests in the plan. The first proves the design's central claim — that every downstream flow works unchanged — rather than assuming it. The second proves the transactional rollback, the one genuinely novel behaviour.

**Files:**
- Modify: `backend/src/integrationTest/java/com/fashionrental/receipt/RentalFlowIT.java`
- Create: `backend/src/integrationTest/java/com/fashionrental/receipt/AdHocCheckoutIT.java`

**Interfaces:**
- Consumes: everything from Tasks 1-4
- Produces: nothing consumed by later tasks

- [ ] **Step 1: Write the failing end-to-end test**

Add to `RentalFlowIT` (which is `@Transactional` at class level — fine for this test):

```java
    @Test
    void ad_hoc_only_rental_completes_checkout_and_return() {
        Customer customer = new Customer();
        customer.setName("Anil");
        customer.setPhone("9800011122");
        customer.setCustomerType(Customer.CustomerType.MISC);
        customer.setIsActive(true);
        customer = customerRepository.save(customer);

        OffsetDateTime start = OffsetDateTime.parse("2026-04-18T10:00:00+05:30");
        OffsetDateTime end = OffsetDateTime.parse("2026-04-21T10:00:00+05:30");

        ReceiptResponse receipt = checkoutService.createReceipt(new CheckoutRequest(
                customer.getId(), start, end,
                List.of(),
                List.of(new AdHocLineItem("Walk-in Lehenga", "Free size", 500, 1000, 1)),
                null
        ));

        assertThat(receipt.lineItems()).hasSize(1);
        assertThat(receipt.lineItems().get(0).itemName()).isEqualTo("Walk-in Lehenga");
        assertThat(receipt.lineItems().get(0).itemSize()).isEqualTo("Free size");
        assertThat(receipt.lineItems().get(0).lineRent()).isEqualTo(500);
        assertThat(receipt.lineItems().get(0).itemPurchaseRate()).isNull();
        assertThat(receipt.totalRent()).isEqualTo(500);
        assertThat(receipt.totalDeposit()).isEqualTo(1000);

        // ── return on time → full deposit back ──
        InvoiceResponse invoice = returnService.processReturn(receipt.id(), new ProcessReturnRequest(
                end,
                List.of(new ReturnLineItem(receipt.lineItems().get(0).id(), false, null, null)),
                "CASH", null, null
        ));

        assertThat(invoice.totalLateFee()).isZero();
        assertThat(invoice.depositToReturn()).isEqualTo(1000);
        assertThat(invoice.transactionType()).isEqualTo("REFUND");
        assertThat(invoice.lineItems()).hasSize(1);
        assertThat(invoice.lineItems().get(0).itemName()).isEqualTo("Walk-in Lehenga");
    }
```

Add imports for `AdHocLineItem`. **Check `ReturnLineItem`'s actual component order before writing this** — run `sed -n '1,40p' backend/src/main/java/com/fashionrental/invoice/model/request/ReturnLineItem.java` and match it; the same applies to `ProcessReturnRequest`.

- [ ] **Step 2: Write the failing rollback test**

Create `AdHocCheckoutIT.java`. It must **not** be `@Transactional` — a test-managed transaction would swallow the rollback being asserted:

```java
package com.fashionrental.receipt;

import com.fashionrental.AbstractIntegrationTest;
import com.fashionrental.common.exception.ConflictException;
import com.fashionrental.customer.Customer;
import com.fashionrental.customer.CustomerRepository;
import com.fashionrental.inventory.Item;
import com.fashionrental.inventory.ItemRepository;
import com.fashionrental.receipt.model.request.AdHocLineItem;
import com.fashionrental.receipt.model.request.CheckoutLineItem;
import com.fashionrental.receipt.model.request.CheckoutRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Deliberately NOT @Transactional: this asserts that CheckoutService's own transaction
 * rolls back. A test-managed transaction would make the ad-hoc rows invisible to the
 * assertion regardless of whether the rollback actually happened.
 */
class AdHocCheckoutIT extends AbstractIntegrationTest {

    @Autowired private CheckoutService checkoutService;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ItemRepository itemRepository;

    @AfterEach
    void cleanUp() {
        itemRepository.deleteAll();
        customerRepository.deleteAll();
    }

    @Test
    void should_not_persist_ad_hoc_items_when_a_catalogue_line_conflicts() {
        Customer customer = new Customer();
        customer.setName("Priya");
        customer.setPhone("9700011122");
        customer.setCustomerType(Customer.CustomerType.MISC);
        customer.setIsActive(true);
        customer = customerRepository.save(customer);

        Item soldOut = new Item();
        soldOut.setName("Sold Out Sherwani");
        soldOut.setCategory(Item.Category.COSTUME);
        soldOut.setItemType(Item.ItemType.INDIVIDUAL);
        soldOut.setRate(300);
        soldOut.setDeposit(1000);
        soldOut.setQuantity(0);          // nothing available → ConflictException
        soldOut.setIsActive(true);
        soldOut = itemRepository.save(soldOut);

        long itemsBefore = itemRepository.count();

        OffsetDateTime start = OffsetDateTime.parse("2026-04-18T10:00:00+05:30");
        OffsetDateTime end = OffsetDateTime.parse("2026-04-21T10:00:00+05:30");

        assertThatThrownBy(() -> checkoutService.createReceipt(new CheckoutRequest(
                customer.getId(), start, end,
                List.of(new CheckoutLineItem(soldOut.getId(), 1)),
                List.of(new AdHocLineItem("Orphan Lehenga", "M", 500, 1000, 1)),
                null
        ))).isInstanceOf(ConflictException.class);

        assertThat(itemRepository.count())
                .as("the ad-hoc item must have been rolled back with the receipt")
                .isEqualTo(itemsBefore);
    }
}
```

**Ordering caveat:** this depends on the catalogue line being processed before the ad-hoc loop, which Task 3 Step 3 arranges. If the ad-hoc loop is ever moved first, the rollback still holds — the assertion is on the transaction, not the order.

- [ ] **Step 3: Run both to verify they fail**

Run: `cd backend && ./gradlew integrationTest --tests '*AdHocCheckoutIT' --tests '*RentalFlowIT'`
Expected: FAIL. (On podman, export `DOCKER_HOST` and `TESTCONTAINERS_RYUK_DISABLED=true` first — see CLAUDE.md.)

- [ ] **Step 4: Fix whatever they surface**

No new production code is planned here — Tasks 1-4 should already satisfy both. If they do not, the failure is real and belongs to whichever earlier task owns it. Common causes: a missed `new CheckoutRequest(...)` call site, or `ReturnLineItem`'s component order differing from Step 1's guess.

- [ ] **Step 5: Run the full integration suite**

Run: `cd backend && ./gradlew integrationTest`
Expected: PASS — all classes.

- [ ] **Step 6: Commit**

```bash
git add backend/src/integrationTest/java/com/fashionrental/receipt/
git commit -m "test(checkout): cover ad-hoc rental lifecycle and transactional rollback"
```

---

## Task 6: Frontend types and cart keyed on `lineKey`

**Files:**
- Modify: `frontend/src/types/receipt.ts`
- Modify: `frontend/src/hooks/useCart.ts`
- Test: `frontend/src/hooks/useCart.test.ts`

**Interfaces:**
- Consumes: nothing from backend tasks (the wire shape is agreed, not imported)
- Produces: `CartItem = CatalogueCartItem | AdHocCartItem`, both with `lineKey: string` and `kind`; `AdHocLineItem`; `useCart` methods keyed by `lineKey`

- [ ] **Step 1: Write the failing tests**

Replace the `anItem` helper in `useCart.test.ts` and add cases:

```ts
import { describe, it, expect, beforeEach } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { useCart } from './useCart'
import type { CartItem, CatalogueCartItem, AdHocCartItem } from '../types/receipt'

const aCatalogueItem = (overrides: Partial<CatalogueCartItem> = {}): CartItem => ({
  kind: 'CATALOGUE', lineKey: 'i1', itemId: 'i1', itemName: 'Sherwani',
  itemType: 'INDIVIDUAL', category: 'COSTUME', size: null, componentNames: null,
  thumbnailUrl: null, rate: 100, deposit: 500, quantity: 1, availableQuantity: 3,
  ...overrides,
})

const anAdHocItem = (overrides: Partial<AdHocCartItem> = {}): CartItem => ({
  kind: 'ADHOC', lineKey: crypto.randomUUID(), itemName: 'Walk-in Lehenga',
  size: 'Free size', flatPrice: 500, deposit: 1000, quantity: 1,
  ...overrides,
})

describe('useCart', () => {
  beforeEach(() => localStorage.clear())

  it('creates a cart, adds/increments/updates/removes items, then clears', () => {
    const { result } = renderHook(() => useCart())

    act(() => result.current.createCart('2026-04-18T10:00:00+05:30', '2026-04-19T10:00:00+05:30', 1))
    expect(result.current.cart?.items).toHaveLength(0)

    act(() => result.current.addItem(aCatalogueItem()))
    expect(result.current.cart?.items).toHaveLength(1)

    act(() => result.current.addItem(aCatalogueItem())) // same item → increments
    expect(result.current.cart?.items[0].quantity).toBe(2)

    act(() => result.current.updateQuantity('i1', 5))
    expect(result.current.cart?.items[0].quantity).toBe(5)

    act(() => result.current.removeItem('i1'))
    expect(result.current.cart?.items).toHaveLength(0)

    act(() => result.current.clearCart())
    expect(result.current.cart).toBeNull()
  })

  it('appends each ad-hoc entry as its own line instead of merging', () => {
    const { result } = renderHook(() => useCart())
    act(() => result.current.createCart('s', 'e', 3))

    act(() => result.current.addItem(anAdHocItem({ itemName: 'Red Sherwani', flatPrice: 500 })))
    act(() => result.current.addItem(anAdHocItem({ itemName: 'Red Sherwani', flatPrice: 800 })))

    expect(result.current.cart?.items).toHaveLength(2)
    expect(result.current.cart?.items.map(i => i.quantity)).toEqual([1, 1])
  })

  it('removes an ad-hoc line by its lineKey without touching its twin', () => {
    const { result } = renderHook(() => useCart())
    act(() => result.current.createCart('s', 'e', 3))

    const first = anAdHocItem({ lineKey: 'adhoc-1', itemName: 'Red Sherwani' })
    const second = anAdHocItem({ lineKey: 'adhoc-2', itemName: 'Red Sherwani' })
    act(() => result.current.addItem(first))
    act(() => result.current.addItem(second))

    act(() => result.current.removeItem('adhoc-1'))

    expect(result.current.cart?.items).toHaveLength(1)
    expect(result.current.cart?.items[0].lineKey).toBe('adhoc-2')
  })

  it('persists to and loads from localStorage under the v2 key', () => {
    const first = renderHook(() => useCart())
    act(() => first.result.current.createCart('s', 'e', 2))
    expect(JSON.parse(localStorage.getItem('rental_cart_v2')!).rentalDays).toBe(2)

    const second = renderHook(() => useCart())
    expect(second.result.current.cart?.rentalDays).toBe(2)
  })

  it('ignores a v1 cart left over from a previous deploy', () => {
    localStorage.setItem('rental_cart', JSON.stringify({
      startDatetime: 's', endDatetime: 'e', rentalDays: 2,
      items: [{ itemId: 'i1', itemName: 'Old', rate: 100, deposit: 500, quantity: 1 }],
    }))

    const { result } = renderHook(() => useCart())
    expect(result.current.cart).toBeNull()
  })

  it('recovers from corrupt localStorage by starting empty', () => {
    localStorage.setItem('rental_cart_v2', 'not-json')
    const { result } = renderHook(() => useCart())
    expect(result.current.cart).toBeNull()
  })
})
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd frontend && pnpm test -- src/hooks/useCart.test.ts`
Expected: FAIL — `CatalogueCartItem` / `AdHocCartItem` are not exported, and the v2 key does not exist.

- [ ] **Step 3: Rewrite the cart types**

In `src/types/receipt.ts`, replace the `CartItem` interface (line 94) with:

```ts
interface CartItemBase {
  lineKey: string          // stable cart key and React key
  itemName: string
  size: string | null
  quantity: number
  deposit: number          // per unit
}

export interface CatalogueCartItem extends CartItemBase {
  kind: 'CATALOGUE'
  itemId: string
  itemType: 'INDIVIDUAL' | 'PACKAGE'
  category: string
  componentNames: string[] | null   // null for INDIVIDUAL; ["Name ×qty", ...] for PACKAGE
  thumbnailUrl: string | null
  rate: number                      // per day
  availableQuantity: number
}

export interface AdHocCartItem extends CartItemBase {
  kind: 'ADHOC'
  flatPrice: number                 // per unit, for the WHOLE rental — not per day
}

export type CartItem = CatalogueCartItem | AdHocCartItem
```

- [ ] **Step 4: Add the request types**

In the same file, add `AdHocLineItem` and extend both request shapes:

```ts
export interface AdHocLineItem {
  name: string
  size: string | null
  flatPrice: number
  deposit: number
  quantity: number
}
```

Add `adHocItems: AdHocLineItem[]` to `CheckoutPreviewRequest` and `CheckoutRequest`, and change `PreviewLineItem.itemId` to `string | null`.

- [ ] **Step 5: Key the cart on `lineKey`**

Rewrite `src/hooks/useCart.ts`'s storage key and three methods:

```ts
const STORAGE_KEY = 'rental_cart_v2'
```

```ts
  const addItem = useCallback((item: CartItem) => {
    const existing = item.kind === 'CATALOGUE'
      ? cart!.items.find(i => i.lineKey === item.lineKey)
      : undefined
    setCart({
      ...cart!,
      items: existing
        ? cart!.items.map(i => i.lineKey === item.lineKey ? { ...i, quantity: i.quantity + 1 } : i)
        : [...cart!.items, item],
    })
  }, [cart, setCart])

  const removeItem = useCallback((lineKey: string) => {
    setCart({ ...cart!, items: cart!.items.filter(i => i.lineKey !== lineKey) })
  }, [cart, setCart])

  const updateQuantity = useCallback((lineKey: string, quantity: number) => {
    setCart({ ...cart!, items: cart!.items.map(i => i.lineKey === lineKey ? { ...i, quantity } : i) })
  }, [cart, setCart])
```

Ad-hoc entries never match the `existing` lookup, so they always append — that is what makes two identically-named typed products two independent lines.

Also export the new types alongside the existing re-export:

```ts
export type { Cart, CartItem, CatalogueCartItem, AdHocCartItem }
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd frontend && pnpm test -- src/hooks/useCart.test.ts`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/types/receipt.ts frontend/src/hooks/useCart.ts frontend/src/hooks/useCart.test.ts
git commit -m "feat(cart): model cart items as a catalogue/ad-hoc union keyed on lineKey

Bumps the storage key to rental_cart_v2 so a cart held across the deploy is
dropped rather than parsed into the new shape and rendered broken."
```

---

## Task 7: Make `CheckoutPage` pricing-aware

Task 6 will have broken `CheckoutPage.tsx` compilation. This task fixes every site the compiler flags — which is precisely the mechanism that stops the flat-price bug reaching production.

**Files:**
- Modify: `frontend/src/pages/checkout/CheckoutPage.tsx` (lines 166, 194, 359, 487, 503-570)
- Modify: `frontend/src/pages/checkout/CheckoutPage.test.tsx` (lines 15-31)
- Create: `frontend/src/pages/checkout/cartPricing.ts`
- Test: `frontend/src/pages/checkout/CheckoutPage.test.tsx`

**Interfaces:**
- Consumes: `CartItem`, `CatalogueCartItem`, `AdHocCartItem` (Task 6)
- Produces: `cartPricing.ts` exporting `lineRentOf(item, rentalDays)` and `perDayRateOf(item, rentalDays)`, reused by `AdHocEntryScreen` in Task 9

- [ ] **Step 1: See the full damage**

Run: `cd frontend && pnpm type-check`
Expected: FAIL, listing every site. Work through them with the compiler as the checklist — do not guess at the list.

- [ ] **Step 2: Write the failing pricing test**

Add to `CheckoutPage.test.tsx`, and update `baseCartItem` (line 17) to the `CatalogueCartItem` shape plus `CART_STORAGE_KEY = 'rental_cart_v2'` (line 15):

```tsx
  it('totals a mixed cart using flat pricing for ad-hoc lines and per-day for catalogue lines', async () => {
    seedCart([
      { ...baseCartItem, kind: 'CATALOGUE', lineKey: 'i1', rate: 100, deposit: 500, quantity: 1 },
      { kind: 'ADHOC', lineKey: 'a1', itemName: 'Walk-in Lehenga', size: null,
        flatPrice: 500, deposit: 1000, quantity: 2 },
    ])

    renderCheckout()

    // catalogue: 100 * 3 days * 1 = 300 rent + 500 deposit
    // ad-hoc:    500 flat * 2     = 1000 rent + 2000 deposit
    expect(await screen.findByText('₹3,800')).toBeInTheDocument()
  })
```

Match `seedCart` / `renderCheckout` to the helpers already in that file, and set the seeded cart's `rentalDays` to 3.

- [ ] **Step 3: Add the two pricing helpers**

These go in their own module, **not** in `CheckoutPage.tsx`. `AdHocEntryScreen` (Task 9) needs them
and `CheckoutPage` imports `AdHocEntryScreen`, so putting them in `CheckoutPage` would create an
import cycle.

Create `frontend/src/pages/checkout/cartPricing.ts`:

```ts
import type { CartItem } from '../../types/receipt'

// Ad-hoc lines carry a flat price for the whole rental; catalogue lines carry a per-day
// rate. Every pricing site must go through these two helpers rather than reaching for
// `.rate` directly, which does not exist on an ad-hoc line.
export function lineRentOf(item: CartItem, rentalDays: number): number {
  return item.kind === 'ADHOC'
    ? item.flatPrice * item.quantity
    : item.rate * rentalDays * item.quantity
}

export function perDayRateOf(item: CartItem, rentalDays: number): number {
  return item.kind === 'ADHOC'
    ? Math.round(item.flatPrice / rentalDays)
    : item.rate
}
```

Import them in `CheckoutPage.tsx`:

```tsx
import { lineRentOf, perDayRateOf } from './cartPricing'
```

- [ ] **Step 4: Route every pricing site through them**

`cartTotal` (line 207):

```tsx
  const cartTotal = cart
    ? cart.items.reduce((s, i) => s + lineRentOf(i, cart.rentalDays) + i.deposit * i.quantity, 0)
    : 0
```

Preview columns (lines 560-570):

```tsx
      {
        title: 'Rate/day',
        key: 'rate',
        render: (_: unknown, r: CartItem) =>
          r.kind === 'ADHOC'
            ? `${formatCurrency(perDayRateOf(r, cart!.rentalDays))} (derived)`
            : formatCurrency(r.rate),
      },
      { title: 'Deposit', key: 'deposit', render: (_: unknown, r: CartItem) => formatCurrency(r.deposit) },
      {
        title: 'Line Rent',
        key: 'lineRent',
        render: (_: unknown, r: CartItem) => formatCurrency(lineRentOf(r, cart!.rentalDays)),
      },
      {
        title: 'Line Deposit',
        key: 'lineDeposit',
        render: (_: unknown, r: CartItem) => formatCurrency(r.deposit * r.quantity),
      },
```

- [ ] **Step 5: Narrow the catalogue-only sites**

Line 166 (`handleAddToCart`) builds a `CatalogueCartItem` — add `kind: 'CATALOGUE'` and `lineKey: item.id`.

Lines 359 and 487 look a cart entry up by item id; both are catalogue-only contexts:

```tsx
const inCart = cart!.items.find(c => c.kind === 'CATALOGUE' && c.itemId === item.id)
```

Line 504 (`freshItemMap.get(r.itemId)`) must skip ad-hoc rows:

```tsx
          const fresh = r.kind === 'CATALOGUE' ? freshItemMap.get(r.itemId) : undefined
          const category = fresh?.category ?? (r.kind === 'CATALOGUE' ? r.category : null)
          const componentNames = fresh?.componentNames ?? (r.kind === 'CATALOGUE' ? r.componentNames : null)
          const thumbnailUrl = (r.kind === 'CATALOGUE' ? r.thumbnailUrl : null) ?? fresh?.thumbnailUrl ?? null
```

`size` already exists on both variants, so `fresh?.size ?? r.size ?? null` is unchanged.

Line 555 (`updateQuantity(row.itemId, v)`) becomes `updateQuantity(row.lineKey, v)`.

Also give the preview `<Table>` `rowKey="lineKey"` if it currently keys on `itemId`.

- [ ] **Step 6: Verify**

Run: `cd frontend && pnpm type-check && pnpm lint && pnpm test`
Expected: all PASS, including the sibling suites `pages/lineItemThumbnails.test.tsx` and `pages/pages.render.test.tsx`.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/pages/checkout/cartPricing.ts \
        frontend/src/pages/checkout/CheckoutPage.tsx \
        frontend/src/pages/checkout/CheckoutPage.test.tsx
git commit -m "fix(checkout): compute totals per pricing kind instead of assuming a per-day rate"
```

---

## Task 8: `AdHocItemModal`

**Files:**
- Create: `frontend/src/pages/checkout/AdHocItemModal.tsx`
- Test: `frontend/src/pages/checkout/AdHocItemModal.test.tsx` (create)

**Interfaces:**
- Consumes: `AdHocCartItem` (Task 6)
- Produces: `<AdHocItemModal open rentalDays onCancel onAdd />` where `onAdd: (item: AdHocCartItem) => void`

- [ ] **Step 1: Write the failing test**

```tsx
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import AdHocItemModal from './AdHocItemModal'

describe('AdHocItemModal', () => {
  it('emits an ad-hoc cart item with a generated lineKey', async () => {
    const onAdd = vi.fn()
    render(<AdHocItemModal open rentalDays={3} onCancel={() => {}} onAdd={onAdd} />)

    await userEvent.type(screen.getByLabelText(/product name/i), 'Walk-in Lehenga')
    await userEvent.type(screen.getByLabelText(/size/i), 'Free size')
    await userEvent.clear(screen.getByLabelText(/total price/i))
    await userEvent.type(screen.getByLabelText(/total price/i), '500')
    await userEvent.clear(screen.getByLabelText(/deposit/i))
    await userEvent.type(screen.getByLabelText(/deposit/i), '1000')
    await userEvent.click(screen.getByRole('button', { name: /add to cart/i }))

    expect(onAdd).toHaveBeenCalledTimes(1)
    expect(onAdd.mock.calls[0][0]).toMatchObject({
      kind: 'ADHOC', itemName: 'Walk-in Lehenga', size: 'Free size',
      flatPrice: 500, deposit: 1000, quantity: 1,
    })
    expect(onAdd.mock.calls[0][0].lineKey).toEqual(expect.any(String))
  })

  it('shows the derived per-day rate used for late fees', async () => {
    render(<AdHocItemModal open rentalDays={3} onCancel={() => {}} onAdd={() => {}} />)

    await userEvent.clear(screen.getByLabelText(/total price/i))
    await userEvent.type(screen.getByLabelText(/total price/i), '500')

    expect(await screen.findByText(/₹167\/day/)).toBeInTheDocument()
  })

  it('refuses to submit without a product name', async () => {
    const onAdd = vi.fn()
    render(<AdHocItemModal open rentalDays={3} onCancel={() => {}} onAdd={onAdd} />)

    await userEvent.click(screen.getByRole('button', { name: /add to cart/i }))

    expect(onAdd).not.toHaveBeenCalled()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && pnpm test -- src/pages/checkout/AdHocItemModal.test.tsx`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement the modal**

Create `AdHocItemModal.tsx`:

```tsx
import { Form, Input, InputNumber, Modal, Typography } from 'antd'
import type { AdHocCartItem } from '../../types/receipt'
import { formatCurrency } from '../../utils/currency'

interface AdHocItemModalProps {
  open: boolean
  rentalDays: number
  onCancel: () => void
  onAdd: (item: AdHocCartItem) => void
}

interface FormValues {
  itemName: string
  size?: string
  flatPrice: number
  deposit: number
  quantity: number
}

export default function AdHocItemModal({ open, rentalDays, onCancel, onAdd }: AdHocItemModalProps) {
  const [form] = Form.useForm<FormValues>()
  const flatPrice = Form.useWatch('flatPrice', form)

  const perDayRate =
    typeof flatPrice === 'number' && rentalDays > 0 ? Math.round(flatPrice / rentalDays) : null

  async function handleSubmit() {
    const values = await form.validateFields()
    onAdd({
      kind: 'ADHOC',
      lineKey: crypto.randomUUID(),
      itemName: values.itemName.trim(),
      size: values.size?.trim() || null,
      flatPrice: values.flatPrice,
      deposit: values.deposit,
      quantity: values.quantity,
    })
    form.resetFields()
  }

  return (
    <Modal
      open={open}
      title="Add a product not in inventory"
      okText="Add to cart"
      onOk={handleSubmit}
      onCancel={() => { form.resetFields(); onCancel() }}
      destroyOnClose
    >
      <Form form={form} layout="vertical" initialValues={{ deposit: 0, quantity: 1 }}>
        <Form.Item
          label="Product name"
          name="itemName"
          rules={[{ required: true, whitespace: true, message: 'Enter a product name' }]}
        >
          <Input autoFocus placeholder="e.g. Red Sherwani" />
        </Form.Item>

        <Form.Item label="Size" name="size">
          <Input placeholder="e.g. L, Free size" />
        </Form.Item>

        <Form.Item
          label="Total price for the whole rental"
          name="flatPrice"
          rules={[{ required: true, message: 'Enter the total price' }]}
          extra={
            perDayRate !== null ? (
              <Typography.Text type="secondary">
                {formatCurrency(perDayRate)}/day — used to calculate late fees
              </Typography.Text>
            ) : null
          }
        >
          <InputNumber min={1} precision={0} style={{ width: '100%' }} prefix="₹" />
        </Form.Item>

        <Form.Item
          label="Deposit"
          name="deposit"
          rules={[{ required: true, message: 'Enter a deposit (0 if none)' }]}
        >
          <InputNumber min={0} precision={0} style={{ width: '100%' }} prefix="₹" />
        </Form.Item>

        <Form.Item
          label="Quantity"
          name="quantity"
          rules={[{ required: true, message: 'Enter a quantity' }]}
        >
          <InputNumber min={1} precision={0} style={{ width: '100%' }} />
        </Form.Item>
      </Form>
    </Modal>
  )
}
```

Two details that are load-bearing rather than stylistic:

- The price label says **"Total price for the whole rental"**, not "Price". This is the single most likely point of staff error, and the `extra` helper text showing the derived per-day figure is what makes the late-fee basis from §3.6 visible at the point of sale rather than a surprise at return.
- `precision={0}` on both money fields enforces whole rupees, per Global Constraints.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd frontend && pnpm test -- src/pages/checkout/AdHocItemModal.test.tsx`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/checkout/AdHocItemModal.tsx frontend/src/pages/checkout/AdHocItemModal.test.tsx
git commit -m "feat(checkout): add the typed-in product entry modal"
```

---

## Task 9: `AdHocEntryScreen` and the `adhoc` screen in `CheckoutPage`

**Files:**
- Create: `frontend/src/pages/checkout/AdHocEntryScreen.tsx`
- Modify: `frontend/src/pages/checkout/CheckoutPage.tsx` (lines 42-55, 189-196)
- Test: `frontend/src/pages/checkout/CheckoutPage.test.tsx`

**Interfaces:**
- Consumes: `AdHocItemModal` (Task 8); `lineRentOf` (Task 7); `useCart` (Task 6)
- Produces: `CheckoutPage` accepts `initialScreen?: Screen`; `buildRequest` emits `{ items, adHocItems }`

- [ ] **Step 1: Write the failing test**

```tsx
  it('opens on the typed-in entry screen when initialScreen is adhoc', async () => {
    renderCheckout({ initialScreen: 'adhoc' })

    expect(await screen.findByRole('button', { name: /add product/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /browse inventory/i })).toBeInTheDocument()
  })
```

Extend the file's `renderCheckout` helper to forward props to `<CheckoutPage />`.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && pnpm test -- src/pages/checkout/CheckoutPage.test.tsx`
Expected: FAIL — `initialScreen` is not a prop.

- [ ] **Step 3: Build `AdHocEntryScreen`**

Create `AdHocEntryScreen.tsx`:

```tsx
import { useState } from 'react'
import { Button, Card, Empty, List, Space, Typography } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import PageHeader from '../../components/common/PageHeader'
import AdHocItemModal from './AdHocItemModal'
import { lineRentOf } from './cartPricing'
import { formatCurrency } from '../../utils/currency'
import type { Cart, CartItem } from '../../types/receipt'

interface AdHocEntryScreenProps {
  cart: Cart | null
  rentalDays: number
  onAddItem: (item: CartItem) => void
  onRemoveItem: (lineKey: string) => void
  onBrowse: () => void
  onReview: () => void
  onSetUpDates: () => void
}

export default function AdHocEntryScreen({
  cart, rentalDays, onAddItem, onRemoveItem, onBrowse, onReview, onSetUpDates,
}: AdHocEntryScreenProps) {
  const [showModal, setShowModal] = useState(false)

  if (!cart) {
    return (
      <>
        <PageHeader title="Quick Rental" />
        <Card>
          <Empty description="Set the rental dates to begin" />
          <div style={{ textAlign: 'center', marginTop: 16 }}>
            <Button type="primary" onClick={onSetUpDates}>Set rental dates</Button>
          </div>
        </Card>
      </>
    )
  }

  return (
    <>
      <PageHeader title="Quick Rental" />
      <Card>
        {cart.items.length === 0 ? (
          <Empty description="No products yet — type the first one in" />
        ) : (
          <List
            dataSource={cart.items}
            rowKey={(item) => item.lineKey}
            renderItem={(item) => (
              <List.Item
                actions={[
                  <Button key="remove" type="link" danger onClick={() => onRemoveItem(item.lineKey)}>
                    Remove
                  </Button>,
                ]}
              >
                <List.Item.Meta
                  title={item.itemName}
                  description={[item.size, `Qty ${item.quantity}`].filter(Boolean).join(' · ')}
                />
                <Typography.Text strong>
                  {formatCurrency(lineRentOf(item, rentalDays))}
                </Typography.Text>
              </List.Item>
            )}
          />
        )}

        <Space style={{ marginTop: 16 }} wrap>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setShowModal(true)}>
            Add product
          </Button>
          <Button onClick={onBrowse}>Browse inventory</Button>
          <Button type="primary" onClick={onReview} disabled={cart.items.length === 0}>
            Review
          </Button>
        </Space>
      </Card>

      <AdHocItemModal
        open={showModal}
        rentalDays={rentalDays}
        onCancel={() => setShowModal(false)}
        onAdd={(item) => { onAddItem(item); setShowModal(false) }}
      />
    </>
  )
}
```

The list renders `cart.items`, not just the ad-hoc ones, so a rental that started here and picked up catalogue items via **Browse inventory** shows as one coherent order. `lineRentOf` handles both kinds, which is why Task 7 put it in its own `cartPricing` module.

Adjust the `onSetUpDates` wiring in `CheckoutPage` to open the existing create-cart modal (`handleOpenCreateModal`, line 143) — reuse it rather than duplicating the date picker.

- [ ] **Step 4: Wire it into `CheckoutPage`**

```tsx
type Screen = 'home' | 'adhoc' | 'browse' | 'preview' | 'customer'

export default function CheckoutPage({ initialScreen }: { initialScreen?: Screen } = {}) {
  ...
  const [screen, setScreen] = useState<Screen>(initialScreen ?? (cart ? 'browse' : 'home'))
```

Add the branch alongside the other screen blocks:

```tsx
  if (screen === 'adhoc') {
    return (
      <AdHocEntryScreen
        cart={cart}
        rentalDays={cart?.rentalDays ?? 1}
        onSetUpDates={handleOpenCreateModal}
        onAddItem={addItem}
        onRemoveItem={removeItem}
        onBrowse={() => setScreen('browse')}
        onReview={() => setScreen('preview')}
      />
    )
  }
```

`handleConfirmCreate` (line 151) currently ends with `setScreen('browse')`. Change it to return to
whichever screen the user came from, so confirming dates on `/quick-rental` lands back on the
typed-in screen rather than dumping them into inventory browse:

```tsx
  const [screenAfterCreate, setScreenAfterCreate] = useState<Screen>('browse')

  function handleOpenCreateModal() {
    setScreenAfterCreate(screen === 'adhoc' ? 'adhoc' : 'browse')
    ...                                  // rest of the existing body unchanged
  }
```

and in `handleConfirmCreate`, replace `setScreen('browse')` with `setScreen(screenAfterCreate)`.

- [ ] **Step 5: Partition the request**

Replace `buildRequest` (line 189):

```tsx
  function buildRequest(customerId: string): CheckoutRequest {
    return {
      customerId,
      startDatetime: cart!.startDatetime,
      endDatetime: cart!.endDatetime,
      items: cart!.items
        .filter((i): i is CatalogueCartItem => i.kind === 'CATALOGUE')
        .map(i => ({ itemId: i.itemId, quantity: i.quantity })),
      adHocItems: cart!.items
        .filter((i): i is AdHocCartItem => i.kind === 'ADHOC')
        .map(i => ({
          name: i.itemName,
          size: i.size,
          flatPrice: i.flatPrice,
          deposit: i.deposit,
          quantity: i.quantity,
        })),
    }
  }
```

- [ ] **Step 6: Add the browse-screen entry point**

This is the in-flow entry point from the spec's §1 — the same modal, reached from the normal
checkout flow. Add the state and the modal to `CheckoutPage`:

```tsx
  const [showAdHocModal, setShowAdHocModal] = useState(false)
```

In the `browse` screen's JSX, alongside the existing search and filter controls:

```tsx
            <Button icon={<PlusOutlined />} onClick={() => setShowAdHocModal(true)}>
              Add custom product
            </Button>
```

and, inside the same returned fragment as the existing `<ItemBrowseModal />`:

```tsx
          <AdHocItemModal
            open={showAdHocModal}
            rentalDays={cart!.rentalDays}
            onCancel={() => setShowAdHocModal(false)}
            onAdd={(item) => { addItem(item); setShowAdHocModal(false) }}
          />
```

`PlusOutlined` is already imported at the top of the file (line 26).

Add the type imports this task needs:

```tsx
import type { CartItem, CatalogueCartItem, AdHocCartItem, CheckoutRequest } from '../../types/receipt'
```

- [ ] **Step 7: Verify**

Run: `cd frontend && pnpm type-check && pnpm lint && pnpm test`
Expected: all PASS.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/pages/checkout/
git commit -m "feat(checkout): add typed-in entry screen and send ad-hoc lines to the API"
```

---

## Task 10: Route and navigation

**Files:**
- Modify: `frontend/src/components/layout/AppLayout.tsx:46-63`
- Modify: `frontend/src/components/layout/Sidebar.tsx:13-21`
- Test: `frontend/src/components/layout/layout.test.tsx`

**Interfaces:**
- Consumes: `CheckoutPage` with `initialScreen` (Task 9)
- Produces: the `/quick-rental` route

- [ ] **Step 1: Write the failing test**

```tsx
  it('shows Quick Rental in the staff nav', () => {
    renderWithRole('EXECUTIVE')
    expect(screen.getByRole('menuitem', { name: /quick rental/i })).toBeInTheDocument()
  })
```

Match the existing helpers in `layout.test.tsx`.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && pnpm test -- src/components/layout/layout.test.tsx`
Expected: FAIL — no such menu item.

- [ ] **Step 3: Add the nav item**

In `Sidebar.tsx`, in `NAV_ITEMS` immediately after the `/checkout` entry:

```ts
  { key: '/quick-rental', label: 'Quick Rental', roles: ['OWNER', 'EXECUTIVE'] },
```

- [ ] **Step 4: Add the route**

In `AppLayout.tsx`, replace the checkout route and add its sibling:

```tsx
            <Route path="/checkout" element={<CheckoutPage key="checkout" />} />
            <Route path="/quick-rental" element={<CheckoutPage key="quick" initialScreen="adhoc" />} />
```

The distinct `key` is required, not cosmetic: without it React reuses the mounted instance and its screen state when navigating between the two routes, so `/quick-rental` would open on whatever screen `/checkout` was last showing.

- [ ] **Step 5: Verify**

Run: `cd frontend && pnpm type-check && pnpm lint && pnpm test`
Expected: all PASS.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/layout/
git commit -m "feat(nav): add the Quick Rental tab"
```

---

## Task 11: Verify against the running application

Green tests do not prove the `is_ad_hoc` predicate is wired into the query the UI actually calls, nor that Flyway applied cleanly to a database with existing rows. This task is not optional.

**Files:** none — verification only.

- [ ] **Step 1: Bring up the stack**

```bash
docker-compose up -d
cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev'
# in a second terminal
cd frontend && pnpm dev
```

The backend must start cleanly — `ddl-auto: validate` failing here means the entity and migration disagree.

- [ ] **Step 2: Walk the Quick Rental flow**

In the browser at `http://localhost:5173`, log in, open **Quick Rental**, set dates, add a typed product (name `Walk-in Lehenga`, size `Free size`, total price `500`, deposit `1000`, quantity `1`), pick a customer, and create the receipt.

Confirm: the preview shows **₹500** line rent for a 3-day rental (not ₹1,500), and a derived **₹167/day**.

- [ ] **Step 3: Confirm containment**

Open **Inventory** and search `Walk-in Lehenga`. Expected: **no result.** Then open **New Rental → browse** and search the same. Expected: **no result.**

This is the single assertion the unit tests cannot make for you.

- [ ] **Step 4: Confirm the return settles**

Open **Active Rentals**, find the receipt, process the return with no damage. Expected: a REFUND invoice for ₹1,000, and the product name rendered correctly on both the invoice and its public share page.

- [ ] **Step 5: Confirm the mixed flow**

Start at **New Rental**, add a catalogue item, then use **Add custom product**. Expected: the preview totals both correctly — catalogue at rate × days × qty, ad-hoc at flat × qty.

- [ ] **Step 6: Run the full suites one last time**

```bash
cd backend && ./gradlew test integrationTest
cd frontend && pnpm type-check && pnpm lint && pnpm test
```

Expected: all PASS. Report actual output — do not claim completion without it.

---

## Notes for the implementer

- **The flat-price rule is the thing to get right.** `lineRent = flatPrice × quantity`. If you catch yourself writing `× rentalDays` for an ad-hoc line, stop and re-read §3.5 of the spec.
- **`rateSnapshot` is not decorative.** It is the basis for late fees (`BillingService:44`). An ad-hoc line with `rateSnapshot = 0` would silently make that item's late fee zero forever.
- **Do not add an "is this ad-hoc?" check to the return, invoice, reporting, or customer-history code.** The whole point of the hidden-item design is that those paths never learn about ad-hoc items. If one of them seems to need a change, something earlier is wrong — raise it rather than patching downstream.
- **Ask before pushing or opening a PR** (CLAUDE.md). Commits as you go are expected.
