# Checkout & Receipt Management — Feature Showcase

Stories covered: US-301, US-302, US-303, US-304, US-305, US-502

---

## Feature Summary

| Story | Title | Status |
|-------|-------|--------|
| US-301 | Create rental receipt (checkout flow) | Complete |
| US-302 | Auto-calculate billing from datetimes | Complete |
| US-303 | Multi-item support per receipt | Complete |
| US-304 | View active receipts | Complete |
| US-305 | View overdue receipts with duration | Complete |
| US-502 | Availability guard — prevent double booking | Complete |

---

## Architecture Decisions

### Whole rupees — not paise

All monetary fields (`total_rent`, `total_deposit`, `grand_total`, `rate_snapshot`, `deposit_snapshot`, `line_rent`, `line_deposit`) are `INTEGER` in PostgreSQL and `int` in Java storing whole rupees. The feature story used `_Paise` naming — this was intentionally corrected to match the project convention. No division or multiplication by 100 anywhere.

### Two-phase checkout: stateless preview then atomic creation

`POST /api/checkout/preview` is read-only — it computes totals and availability without touching the DB. Staff reviews the breakdown, then `POST /api/receipts` runs inside a `@Transactional` block that rechecks availability authoritatively. If another receipt was created between preview and confirm (race condition), the transaction catches it and returns 409 with the specific item name.

### Snapshot pricing on line items

`rate_snapshot` and `deposit_snapshot` capture the item's rate and deposit at receipt-creation time. If the shop changes an item's price later, existing receipts are unaffected. This is the correct rental domain model — the return/invoice flow (Feature 04) will use snapshots for billing.

### Receipt number generation (IST date-scoped)

Format: `R-YYYYMMDD-NNN`. The date is computed in IST (`ZoneId.of("Asia/Kolkata")`). The sequence number (`NNN`) is derived from `COUNT(*) WHERE receipt_number LIKE 'R-YYYYMMDD-%'` inside the same transaction — safe for single-instance deployment (which matches the PWA use case for a single shop).

### Availability guard: three layers

| Layer | Role |
|---|---|
| Frontend (soft) | Checks `GET /api/items/{id}/availability` when item added — disables confirm if unavailable |
| Preview endpoint | Reports all unavailable items before staff commits |
| `POST /api/receipts` (hard) | Authoritative recheck inside `@Transactional` — the only guarantee |

### Overdue detection is real-time

`ReceiptService.listReceipts()` computes `isOverdue` and `overdueHours` at query time by comparing `endDatetime` to `OffsetDateTime.now()`. No stored flag that can drift — always accurate.

---

## API Surface

### `POST /api/checkout/preview` — Stateless billing preview

**Request:**
```json
{
  "customerId": "uuid",
  "startDatetime": "2026-04-18T10:00:00+05:30",
  "endDatetime": "2026-04-21T10:00:00+05:30",
  "items": [
    { "itemId": "uuid-a", "quantity": 2 },
    { "itemId": "uuid-b", "quantity": 1 }
  ]
}
```

**Response 200:**
```json
{
  "allAvailable": true,
  "lineItems": [
    { "itemName": "Blue Sherwani", "rate": 200, "deposit": 1000, "quantity": 2,
      "rentalDays": 3, "lineRent": 1200, "lineDeposit": 2000, "availableQuantity": 3 },
    { "itemName": "Gold Necklace", "rate": 50, "deposit": 500, "quantity": 1,
      "rentalDays": 3, "lineRent": 150, "lineDeposit": 500, "availableQuantity": 2 }
  ],
  "rentalDays": 3,
  "totalRent": 1350,
  "totalDeposit": 2500,
  "grandTotal": 3850,
  "unavailableItems": []
}
```

**Error cases:** `400` invalid date range | `404` item/customer not found

### `POST /api/receipts` — Create receipt

Same request body as preview. Returns `201 ReceiptResponse` or `409` if availability changed since preview.

### `GET /api/receipts` — List receipts

Query params:
- `?status=GIVEN` — all active
- `?status=GIVEN&overdue=true` — overdue only
- `?status=GIVEN&overdue=false` — active, not overdue

### `GET /api/receipts/{id}` — Receipt detail

Returns full `ReceiptResponse` with all line items.

---

## Billing Formula

```
rentalDays  = max(floor((end - start) / 86400 seconds), 1)
lineRent    = rate × rentalDays × quantity
lineDeposit = deposit × quantity
totalRent   = Σ lineRents
totalDeposit = Σ lineDeposits
grandTotal  = totalRent + totalDeposit
```

**Worked example:**
- Blue Sherwani: ₹200/day, ₹1000 deposit, qty=2, 3 days → rent=₹1200, deposit=₹2000
- Gold Necklace: ₹50/day, ₹500 deposit, qty=1, 3 days → rent=₹150, deposit=₹500
- **Grand Total: ₹3,850**

---

## Test Coverage

### `CheckoutServiceTest` — 7 unit tests

| Test | Covers |
|------|--------|
| `should_calculate_preview_totals_correctly` | Billing formula with 2 items, 3 days |
| `should_flag_unavailable_items_in_preview` | `allAvailable=false`, `unavailableItems` populated |
| `should_throw_validation_when_end_before_start` | Date range guard |
| `should_create_receipt_with_snapshots` | `rateSnapshot = item.getRate()` at creation time |
| `should_throw_conflict_when_item_unavailable_at_creation` | Race condition → 409 |
| `should_throw_not_found_when_customer_missing` | 404 on bad customerId |
| `should_enforce_minimum_1_rental_day` | Sub-24h rental → 1 day |

### `ReceiptServiceTest` — 4 unit tests

| Test | Covers |
|------|--------|
| `should_mark_receipt_as_overdue_when_past_end_datetime` | `isOverdue=true`, `overdueHours` computed |
| `should_not_mark_receipt_as_overdue_when_before_end_datetime` | `isOverdue=false` |
| `should_return_empty_list_when_no_active_receipts` | Empty list path |
| `should_format_item_names_with_quantity` | "Blue Sherwani ×2" format |

### `ReceiptControllerTest` — 7 controller tests (`@WebMvcTest`)

| Test | Covers |
|------|--------|
| `should_return_200_from_preview` | Preview endpoint shape |
| `should_return_201_when_receipt_created` | POST receipts, 201 status |
| `should_return_409_on_conflict` | `ConflictException` → 409 |
| `should_return_400_when_end_before_start` | `ValidationException` → 400 |
| `should_return_400_when_items_empty` | `@NotEmpty` constraint |
| `should_return_401_when_unauthenticated` | Auth guard |
| `should_return_200_for_receipts_list` | GET /api/receipts |

**Total: 97 tests across all test classes — all green.**

---

## Demo Walkthrough

### US-301 / US-302 / US-303 — Create a receipt

1. Click **New Rental** in the sidebar
2. **Step 1 — Customer:** Search by phone or name using the search box → select the customer
3. **Step 2 — Period:** Pick start and end datetimes → live "X days" counter updates
4. **Step 3 — Items:**
   - Search for an item by name → select from dropdown
   - Adjust quantity with the spinner; available units shown beside each row
   - Add more items as needed
5. Click **Preview Order** → totals panel shows: Rental Days, Total Rent, Total Deposit, Grand Total
   - If any item is unavailable: red Alert lists which items and how many are short
6. Click **Confirm & Create Receipt** → receipt detail page opens showing `R-YYYYMMDD-NNN`

**Race condition test:** Create a receipt for an item with qty=1; then try to create another receipt for the same item and dates → second confirm gets 409 with the item name.

### US-304 / US-305 — View active and overdue receipts

1. Click **Active Rentals** in the sidebar
2. **All Active tab:** shows all GIVEN receipts sorted by end date (soonest first)
3. **Overdue tab:** shows only past-due receipts; each card has a red border + "X hrs overdue" or "X days Y hrs overdue" tag
4. Click any receipt card → full detail page with line items, snapshots, and totals
5. Customer phone is a `tel:` link — tap on tablet to call directly

---

## Known Limitations

- `activeRentalsCount` on `CustomerSummaryResponse` is still hardcoded to 0 — will be wired after this feature is merged (query `receipt_line_items` where `status = 'GIVEN'` for that customer)
- "Process Return" button on receipts navigates to the detail page for now — Feature 04 will implement the full return flow
- Receipt number sequence is DB-count-based (not a true DB sequence) — safe for single-instance PWA; would need a PostgreSQL `SEQUENCE` for multi-instance deployment
