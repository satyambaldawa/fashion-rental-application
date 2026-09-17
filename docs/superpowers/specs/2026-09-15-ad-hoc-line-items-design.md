# Design: Ad-Hoc (Typed-In) Line Items in Checkout

**Date:** 2026-09-15
**Status:** Draft for review
**Type:** Feature — backend (schema + checkout service) and frontend (cart model + new tab)

---

## 1. Purpose

Staff cannot always find the product a walk-in customer wants in the inventory catalogue. Today
that blocks the rental entirely: `CheckoutService` resolves every line via
`itemRepository.findById(...).orElseThrow(...)`, so a product that is not in `items` cannot be
billed.

This design lets staff type a product's **name, size, price, deposit and quantity** directly into
the checkout flow and produce a normal Receipt from it — one that draws down no existing catalogue
stock, but behaves like any other rental through return and settlement.

Two entry points, one flow:

- an **Add custom product** action inside the existing `/checkout` flow, and
- a new **Quick Rental** tab (`/quick-rental`) that opens straight into typing, with inventory
  browsing available if the rental turns out to be mixed.

---

## 2. Decisions taken during design

| Question | Decision |
|---|---|
| What document does this produce? | A **normal Receipt** — returnable, settles into a real Invoice. |
| Does the typed product enter Inventory? | **Yes**, as a hidden `items` row flagged `is_ad_hoc`. |
| What does the typed price mean? | **Flat total for the whole rental**, per unit. |
| Is a deposit captured? | **Yes, required** — the field must be filled; `0` is permitted. |
| Can the new tab also browse inventory? | **Yes** — type-first, with a **Browse inventory** button. |
| Frontend structure? | **One flow, route-selected entry screen** (§7). |

### 2.1 Why a hidden inventory row rather than a nullable `item_id`

The alternative was making `receipt_line_items.item_id` nullable with `custom_name` / `custom_size`
snapshot columns. It was measured and rejected:

| | Hidden `items` row | Nullable `item_id` |
|---|---|---|
| Migrations | 1 (add a column to `items`) | 2 (alter `receipt_line_items` **and** `invoice_line_items`, plus a CHECK constraint) |
| Call sites needing change | **1** — the predicate list in `ItemService.java:57` | **12** — every `li.getItem().` dereference |
| Downstream flows | unchanged | each needs null-handling |
| Failure mode if a site is missed | a stray row appears in item browse — visible immediately | `NullPointerException`, often only at *return* time, weeks after the receipt was created |

The 12 dereference sites are `ReportingService:82,164`, `CustomerHistoryService:54`,
`ReceiptService:54`, `BillingService:60,63`, `ReturnService:77,236,237,238,239,240`.

This is not a modelling fudge. An ad-hoc entry genuinely *is* a rentable product with a name, size,
rate, deposit and quantity; `is_ad_hoc` records only that it is not part of the browsable
catalogue.

**Accepted cost:** the `items` table accumulates one-off rows, and any *future* item-level report
must remember to filter them.

---

## 3. Backend

### 3.1 Migration

`backend/src/main/resources/db/migration/V20260915001__add_is_ad_hoc_to_items.sql`

```sql
ALTER TABLE items ADD COLUMN is_ad_hoc BOOLEAN NOT NULL DEFAULT FALSE;
```

No index: the browse query filters `is_ad_hoc = false`, which matches nearly every row, so the
planner would never choose one.

`Item.java` gains the matching field (`@Column(name = "is_ad_hoc", nullable = false) private Boolean
isAdHoc = false;`). Hibernate runs `ddl-auto: validate`, so entity and migration must land together.

### 3.2 Containment — the single filter site

`ItemService.java:55-73` builds the browse/list `Specification`. One predicate is added alongside
the existing `isActive` check:

```java
predicates.add(cb.isFalse(root.get("isAdHoc")));
```

This is the only query that lists items for the Inventory page or checkout browse. Verified:

- `ItemRepository.findByIsActiveTrueOrderByNameAsc` (line 22) has **zero callers** — dead code,
  out of scope for this work, flagged separately.
- The Gallery is decoupled — `GalleryImage` borrows only the `Item.Category` enum and has no
  foreign key to `items`.
- `ReportingService` and `CustomerHistoryService` operate at receipt/invoice level and never group
  by item, so revenue reporting needs no change.

**Known limitation (accepted):** `GET/PUT/DELETE /api/items/{id}` will still act on an ad-hoc row
if its id is supplied directly. The id is visible in `ReceiptLineItemResponse.itemId`. These are
OWNER-only endpoints and reaching them requires hand-constructing a URL, so no guard is being
built. Revisit if ad-hoc ids ever leak into a navigable link.

### 3.3 New request DTO

`receipt/model/request/AdHocLineItem.java`

| Field | Validation | Notes |
|---|---|---|
| `name` | `@NotBlank` | |
| `size` | — | optional |
| `flatPrice` | `@NotNull @Min(1)` | total for the whole rental, **per unit** |
| `deposit` | `@NotNull @Min(0)` | required field; `0` permitted |
| `quantity` | `@NotNull @Min(1)` | |

`CheckoutRequest` and `CheckoutPreviewRequest` each gain `@Valid List<AdHocLineItem> adHocItems`.
`items` drops `@NotEmpty`, and both records gain an `@AssertTrue` method asserting at least one line
exists across the two lists — otherwise an empty request would now validate.

### 3.4 Item row created per ad-hoc entry

Created inside the existing `@Transactional createReceipt`, so a `ConflictException` raised by a
later catalogue line rolls the ad-hoc rows back with the receipt.

| Field | Value | Why |
|---|---|---|
| `name`, `size` | as typed | |
| `category` | `OTHER` | `category` is `NOT NULL`; the form does not ask for one |
| `itemType` | `INDIVIDUAL` | ad-hoc entries are never packages |
| `quantity` | as typed | the row is fully booked by this receipt, so it can never be double-sold |
| `rate` | derived per-day (§3.5) | `NOT NULL` |
| `deposit` | as typed | |
| `isActive` | `true` | **required** — `AvailabilityService:33` returns 0 for inactive items and `CheckoutService:81` throws on them |
| `isAdHoc` | `true` | |
| `purchaseRate` | `null` | |

`purchaseRate = null` is free correctness: `BillingService:61` already raises a clear *"Purchase
cost not available … please enter a fixed damage amount instead"*, and
`ReceiptLineItemResponse.itemPurchaseRate` already gates the percentage UI. Damage-by-percentage
self-disables for ad-hoc items with no new code.

### 3.5 Pricing arithmetic

```
rateSnapshot    = round(flatPrice / rentalDays)   // late-fee basis and display only
depositSnapshot = deposit
lineRent        = flatPrice * quantity            // NOT rate * days * quantity
lineDeposit     = deposit * quantity
```

This requires a second line-item builder alongside `buildLineItem` (`CheckoutService.java:178`),
which derives `lineRent` from the rate.

`rateSnapshot * rentalDays * quantity` may differ from `lineRent` by a rupee or two from rounding.
This breaks nothing: **no code recomputes that product.** `lineRent` is stored and read as-is, and
`rateSnapshot` is consumed in exactly three places — the late-fee calculation
(`BillingService:44`), display, and a `> 0` filter that hides zero-rate package-component lines
(`CustomerHistoryService:53`). All amounts remain whole rupees, per project convention.

### 3.6 Late fees on ad-hoc items — a documented consequence

Late fees are computed from `rateSnapshot` (`BillingService:44`:
`round(rateSnapshot * multiplier * quantity)`). Because a flat price is divided across the rental
days, **ad-hoc items attract proportionally smaller late fees on longer rentals.**

Worked example — a ₹500 flat price on a 3-day rental returned late enough to hit the 1.5× tier:

| | `rateSnapshot` | Late fee at 1.5× |
|---|---|---|
| Ad-hoc, ₹500 flat over 3 days | 167 | **₹250** |
| Catalogue item at ₹500/day | 500 | **₹750** |

Dividing the flat price across the rental days is the only arithmetic consistent with "the typed
price is the total for the whole rental" — charging a full-price-per-day late fee on an item whose
*entire* rental cost ₹500 would be disproportionate. This is intended behaviour, recorded here so
it is not later mistaken for a bug.

### 3.7 Preview

`CheckoutService.preview` computes ad-hoc lines arithmetically and **persists nothing** — no `items`
rows are created on preview. `PreviewLineItem.itemId` becomes nullable and `availableQuantity` is
simply the requested quantity (an ad-hoc product is, by definition, always available).

Note: `POST /api/checkout/preview` is **not currently called by the UI** — the preview screen
computes totals client-side at `CheckoutPage.tsx:207-209`. The endpoint is kept contract-correct
here, but it is not on the critical path.

---

## 4. Frontend — cart model

`types/receipt.ts` — `CartItem` becomes a discriminated union:

```ts
interface CartItemBase {
  lineKey: string          // stable cart key and React key
  itemName: string
  size: string | null
  quantity: number
  deposit: number
}

export interface CatalogueCartItem extends CartItemBase {
  kind: 'CATALOGUE'
  itemId: string
  itemType: 'INDIVIDUAL' | 'PACKAGE'
  category: string
  componentNames: string[] | null
  thumbnailUrl: string | null
  rate: number                       // per day
  availableQuantity: number
}

export interface AdHocCartItem extends CartItemBase {
  kind: 'ADHOC'
  flatPrice: number                  // per unit, whole rental
}

export type CartItem = CatalogueCartItem | AdHocCartItem
```

Blast radius measured before adopting this: **9 access sites** — `CheckoutPage.tsx:167,194,359,487,
504,555` and `useCart.ts:40,41,47,51`. `pnpm type-check` flags every one. That is the point: it
forces the bug in §4.2 to be fixed rather than missed.

`AdHocLineItem`, and `adHocItems` on `CheckoutRequest` / `CheckoutPreviewRequest`, are added to the
same file. `PreviewLineItem.itemId` becomes `string | null`.

### 4.1 `useCart.ts` keys on `lineKey`

`addItem` / `removeItem` / `updateQuantity` switch from `itemId` to `lineKey`. Catalogue items set
`lineKey = itemId`, preserving today's "add the same item twice → quantity 2" merge. Ad-hoc items
get `lineKey = crypto.randomUUID()` and **always append**, so typing "Red Sherwani" twice yields two
independently-priced lines.

**localStorage migration.** `loadCart()` (`useCart.ts:13`) JSON-parses whatever sits under
`rental_cart` with no shape check. A staff member holding a cart across the deploy would get items
lacking `kind` / `lineKey` and a broken screen. The storage key is therefore bumped to
`rental_cart_v2`; a v1 cart is ignored. A stale cart is cheap to rebuild, a half-migrated one is
not.

### 4.2 Cart total

`CheckoutPage.tsx:207-209` currently computes
`i.rate * cart.rentalDays * i.quantity + i.deposit * i.quantity` for every line. That is wrong for a
flat-priced line. It becomes pricing-aware — the deposit term is identical for both kinds, only the
rent term differs:

```
CATALOGUE   rate * rentalDays * quantity  +  deposit * quantity
ADHOC       flatPrice * quantity          +  deposit * quantity
```

### 4.3 Preview table columns

The same divergence applies per row in the preview table, not just to the grand total:

| Column | Line | `CATALOGUE` | `ADHOC` |
|---|---|---|---|
| Rate/day | 560 | `formatCurrency(rate)` | `formatCurrency(round(flatPrice / rentalDays))`, suffixed *(derived)* |
| Line Rent | 565 | `rate * rentalDays * quantity` | `flatPrice * quantity` |
| Deposit | 561 | unchanged | unchanged |
| Line Deposit | 570 | unchanged | unchanged |

Showing the derived per-day figure rather than blanking the column keeps the late-fee basis from
§3.6 visible at the point of sale, consistent with the same figure shown in `AdHocItemModal` (§5).

---

## 5. Frontend — new components

Siblings of the existing `ItemBrowseModal.tsx` / `PackageDetailModal.tsx` in `pages/checkout/`:

- **`AdHocItemModal.tsx`** — the entry form: name, size, price, deposit, quantity. The price field
  is labelled explicitly as the total for the whole rental, and the modal shows the derived per-day
  figure so the late-fee basis in §3.6 is visible at entry time rather than surprising at return.
- **`AdHocEntryScreen.tsx`** — the type-first landing screen: typed lines so far, **Add product**,
  **Browse inventory** (`setScreen('browse')`), **Review**.

---

## 6. Frontend — `CheckoutPage.tsx` delta

Roughly 40 lines, because the two components above carry the new UI:

- `Screen` union (line 46) gains `'adhoc'`; the component gains an `initialScreen?: Screen` prop
- one `if (screen === 'adhoc')` branch delegating to `AdHocEntryScreen`
- `buildRequest` (line 189) partitions the cart into `items` and `adHocItems`
- an **Add custom product** button on the browse screen
- the preview screen's `freshItemMap.get(r.itemId)` lookup (line 504) is skipped for `ADHOC` rows,
  which render `ItemPhotoPlaceholder`

---

## 7. Routing and navigation

`AppLayout.tsx`:

```tsx
<Route path="/checkout"      element={<CheckoutPage key="checkout" />} />
<Route path="/quick-rental"  element={<CheckoutPage key="quick" initialScreen="adhoc" />} />
```

The distinct `key` is required, not cosmetic: without it React reuses the mounted instance and its
screen state when navigating between the two routes.

`Sidebar.tsx` `NAV_ITEMS` gains `{ key: '/quick-rental', label: 'Quick Rental', roles: ['OWNER',
'EXECUTIVE'] }` — both roles, matching the existing access rule for `/checkout`.

### 7.1 Why not a separate page component

Considered and rejected:

- **Extract the shared screens first, then two thin pages.** The better end state —
  `CheckoutPage.tsx` is 759 lines and should be decomposed. But it restructures a revenue-critical
  flow whose only tests (`CheckoutPage.test.tsx`, 3 cases) check thumbnail fallback, and the repo
  has no Playwright specs despite `pnpm test:e2e` being documented. The refactor should *follow*
  end-to-end coverage, not precede it. This design leaves that option fully open.
- **A standalone `QuickRentalPage` with its own implementation.** Duplicates cart, preview, customer
  and submit — and since the new tab must also offer browsing, it would duplicate the browse screen
  too.

---

## 8. Testing

All target test files already exist; this is extension, not new scaffolding.

**`CheckoutServiceTest`**
- `should_price_ad_hoc_line_as_flat_total_regardless_of_rental_days` — ₹500 over 3 days → `lineRent`
  500, not 1500
- `should_multiply_ad_hoc_flat_price_by_quantity`
- `should_derive_per_day_rate_snapshot_from_flat_price` — 500 / 3 → 167
- `should_create_hidden_item_row_for_each_ad_hoc_entry` — asserts `isAdHoc`, `isActive`,
  `category = OTHER`, `purchaseRate = null`
- `should_reject_checkout_when_both_item_lists_are_empty`
- `should_accept_checkout_with_only_ad_hoc_items`

**`ItemServiceTest`**
- `should_exclude_ad_hoc_items_from_item_list`

**`RentalFlowIT`** — the two tests carrying most of this design's risk:
- **Full ad-hoc-only rental → return → invoice.** Exercises the 12 `getItem()` sites this design
  deliberately leaves untouched, converting "everything downstream works unchanged" from an
  assertion in §2.1 into something verified.
- `should_not_persist_ad_hoc_items_when_a_catalogue_line_conflicts` — mixed cart, catalogue item
  unavailable → `ConflictException` → zero orphan `items` rows. The transactional rollback is the
  one genuinely novel behaviour.

**Frontend (Vitest)**
- `useCart`: ad-hoc entries append rather than merge; catalogue items still merge by `lineKey`
- `useCart`: `rental_cart_v2` ignores a v1 cart rather than parsing it
- mixed-cart total: flat line contributes `flatPrice × qty`, per-day line contributes
  `rate × days × qty` — the direct guard on the §4.2 bug
- `AdHocItemModal` validation: blank name / price < 1 / quantity < 1 rejected, deposit 0 accepted
- `CheckoutPage` with `initialScreen="adhoc"` renders the entry screen

**Manual verification before this is called done.** Bring up the real stack
(`docker-compose up -d`, backend on the `dev` profile, `pnpm dev`), create an ad-hoc rental end to
end, then confirm the product does **not** appear in Inventory or item browse, and that the return
settles correctly. Green unit tests would not prove the `is_ad_hoc` predicate is wired into the
query the UI actually calls.

---

## 9. Out of scope

- Promoting a recurring ad-hoc product into a permanent catalogue item (considered; deferred)
- Photos for ad-hoc products — they render `ItemPhotoPlaceholder`
- Deleting or cleaning up historical ad-hoc `items` rows
- Removing the dead `ItemRepository.findByIsActiveTrueOrderByNameAsc`
- Decomposing `CheckoutPage.tsx`, and the end-to-end coverage that should precede it (§7.1)
