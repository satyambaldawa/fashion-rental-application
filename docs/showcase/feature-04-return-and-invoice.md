# Feature 04 — Return & Invoice Showcase

**Stories:** US-401 · US-402 · US-403 · US-404  
**Branch:** `feature/04-return-and-invoice`  
**Status:** ✅ Complete

---

## What Was Built

The full return processing flow: staff selects an active receipt, records the return datetime, marks damaged items, previews the invoice, and confirms — generating a permanent invoice and freeing inventory.

---

## User Stories

| Story | Description | Status |
|-------|-------------|--------|
| US-401 | Process return — select receipt, enter return datetime | ✅ |
| US-402 | Mark items as damaged with % or fixed ₹ amount | ✅ |
| US-403 | Auto-calculate late fees using time-range tiers | ✅ |
| US-404 | Generate & display printable invoice | ✅ |

---

## Business Logic

### Late Fee Algorithm

Overdue hours = `returnDatetime − endDatetime`. The highest tier where `durationFromHours ≤ overdueHours` applies:

| Overdue | Tier Matched | Multiplier |
|---------|-------------|------------|
| 0 hrs (on time / early) | — | 0 (no fee) |
| 1 hr | 0–3 hr tier | 0.5× daily rate |
| 3 hrs (exact) | 3–6 hr tier | 0.75× daily rate |
| 7 hrs | 6–24 hr tier | 1.0× daily rate |
| 25 hrs | 24–48 hr tier | 1.5× daily rate |
| 49 hrs | 48+ hr tier | 2.0× daily rate |
| Overdue, no rules configured | Fallback | 1.5× daily rate |

Late fee applies **per line item**: `round(rateSnapshot × multiplier × quantity)`

### Damage Cost Calculation

| Method | Formula | When Available |
|--------|---------|---------------|
| Percentage of purchase cost | `purchaseRate × damagePercentage / 100` | Only when purchase rate was recorded at item creation |
| Fixed (ad hoc) amount | Staff-entered ₹ amount | Always available |

If no purchase rate is recorded, the UI shows: *"No purchase cost available — please enter a fixed damage amount."*

### Invoice Calculation

```
totalDeductions  = totalLateFee + totalDamageCost
depositToReturn  = max(0, depositCollected − totalDeductions)
balanceOwed      = max(0, totalDeductions − depositCollected)

if balanceOwed > 0  → COLLECT  balanceOwed
else                → REFUND   depositToReturn
```

---

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/receipts/{id}/return/preview` | Calculate charges without saving |
| `POST` | `/api/receipts/{id}/return` | Process return, create invoice (201) |
| `GET`  | `/api/invoices/{id}` | Fetch invoice by ID |

---

## Backend — Key Files

```
backend/src/main/java/com/fashionrental/invoice/
  Invoice.java                         — entity (TransactionType, PaymentMethod enums)
  InvoiceLineItem.java                 — per-item late fee + damage cost
  InvoiceRepository.java
  InvoiceNumberService.java            — INV-YYYYMMDD-NNN sequence (IST)
  BillingService.java                  — pure business logic, no DB dependencies
  ReturnService.java                   — atomic transaction: calculate → save → mark RETURNED
  InvoiceController.java               — 3 endpoints
  model/request/
    ProcessReturnRequest.java
    ReturnLineItem.java
  model/response/
    InvoiceResponse.java
    InvoiceLineItemResponse.java
    ReturnPreviewResponse.java
    ReturnPreviewLineItem.java
```

No inventory availability update needed — the availability query already excludes `RETURNED` receipts automatically.

---

## Frontend — Key Files

```
frontend/src/
  types/invoice.ts                     — all invoice/return types
  api/invoices.ts                      — previewReturn, processReturn, get
  pages/receipts/ProcessReturnPage.tsx — return form with damage controls + preview
  pages/invoices/InvoiceDetailPage.tsx — printable invoice display
```

Routes added:
- `/receipts/:id/return` → `ProcessReturnPage`
- `/invoices/:id` → `InvoiceDetailPage`

---

## ProcessReturnPage — UX Flow

1. **Return datetime** — DatePicker defaulting to now; overdue status shown in red
2. **Per item damage controls:**
   - If item has purchase rate: radio toggle between *Damage %* (of purchase cost) or *Fixed ₹*
   - If no purchase rate: warning shown, only fixed ₹ input available
3. **Calculate Preview** — calls `/return/preview`, shows REFUND/COLLECT callout with breakdown
4. **Confirm button** — enabled only after preview; calls `/return`, navigates to invoice

---

## Invoice Detail Page — Features

- Full financial breakdown: rent charged, deposit collected, late fee, damage cost, deposit returned
- Colour-coded final amount: green = REFUND, red = COLLECT
- Per-item table with size/category, damage indicator, late fee and damage columns
- **Print / Share** button — `window.print()` with `@media print` CSS that isolates the invoice area
- Computer-generated footer with receipt and invoice numbers

---

## Tests

**`BillingServiceTest`** — 11 unit tests covering:

| Test | Scenario |
|------|----------|
| `should_return_zero_late_fee_when_returned_on_time` | No fee at exact return time |
| `should_return_zero_late_fee_when_returned_early` | No fee for early return |
| `should_apply_0_5x_multiplier_for_2_hours_overdue` | Tier 0 boundary |
| `should_apply_0_75x_multiplier_for_exactly_3_hours_overdue` | Tier boundary — higher tier wins at exact boundary |
| `should_apply_1_5x_multiplier_for_25_hours_overdue` | Tier 24–48 hr |
| `should_apply_2_0x_multiplier_for_49_hours_overdue` | Tier 48+ hr |
| `should_apply_1_5x_fallback_when_no_rule_matches_overdue` | Empty rules → fallback |
| `should_return_zero_damage_cost_when_not_damaged` | Clean return |
| `should_calculate_damage_cost_using_purchase_rate_and_percentage` | ₹3000 × 30% = ₹900 |
| `should_throw_when_percentage_used_but_purchase_rate_is_null` | ValidationException raised |
| `should_calculate_damage_cost_by_ad_hoc_amount` | Fixed ₹500 regardless of rate |
| `should_prefer_ad_hoc_amount_over_percentage_when_both_provided` | Ad hoc takes priority |
| `should_return_zero_when_damaged_but_no_amount_specified` | Graceful zero |

---

## Also Shipped in This Branch

### Business Requirement Fixes (pre-Feature 04)

**1. Purchase Rate & Vendor Name on Inventory**
- New fields `purchaseRate` and `vendorName` on `Item` (internal, never in customer-facing responses)
- Migration: `V20260422001__add_purchase_info_to_items.sql`
- Form fields in Add Item page (clearly labelled as shop-records-only)

**2. Receipt Detail — Item Details in Line Items**
- Receipt line item response now includes `itemSize`, `itemCategory`, `itemDescription` (read from item at response time, no snapshot columns needed)
- Migration: `V20260422002` added then reverted by `V20260423001`

**3. Receipt Detail — Printable Layout with T&C**
- Deposit-to-return callout box (blue, prominent)
- Terms & Conditions section: return deadline, late fee policy, damage/loss policy, deposit refund terms
- Print / Share button with `@media print` CSS isolation
