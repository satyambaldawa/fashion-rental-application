# Feature 06 — Reporting Showcase

**Stories:** US-701 · US-702 · US-703  
**Branch:** `feature/06-reporting`  
**Status:** ✅ Complete

---

## What Was Built

Four reporting views giving the shop owner a clear financial picture: monthly revenue trends with a daily chart, daily revenue breakdown, outstanding deposit liability, and overdue rental follow-up list.

---

## User Stories

| Story | Description | Status |
|-------|-------------|--------|
| US-701 | Daily revenue summary — rent, deposits, refunds, late fees, net cash flow | ✅ |
| US-702 | Outstanding deposits — all active rentals with deposit held | ✅ |
| US-703 | Overdue rentals — past-deadline rentals with customer contact info | ✅ |

---

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/reports/daily-revenue?date=2026-04-23` | Revenue summary for one date (defaults to today) |
| `GET` | `/api/reports/monthly-revenue?year=2026&month=4` | Monthly totals + daily breakdown for charting (defaults to current month) |
| `GET` | `/api/reports/outstanding-deposits` | All GIVEN receipts with deposit held |
| `GET` | `/api/reports/overdue-rentals` | All receipts past end_datetime with status GIVEN |

All endpoints are read-only (`@Transactional(readOnly = true)`).

---

## Business Logic

### Daily Revenue (`/daily-revenue`)

```
Receipts created on date  → rentCollected + depositsCollected (money in from new rentals)
Invoices settled on date  → depositsRefunded, collectedFromCustomers, lateFeeIncome, damageIncome

netFlow = rentCollected + depositsCollected + collectedFromCustomers − depositsRefunded
```

Deposits are tracked separately from rent because they are a liability (must be returned to the customer unless offset by damage/late fees).

### Monthly Revenue (`/monthly-revenue`)

Same logic as daily, but for a full calendar month. Returns both the monthly totals and a `dailyBreakdown` array with one entry per calendar day (zeros for inactive days, ensuring a complete chart shape).

### Outstanding Deposits (`/outstanding-deposits`)

All receipts with `status = GIVEN`, sorted by `endDatetime ASC`. Total outstanding = sum of all held deposits. Each entry includes `daysSinceRented` (days since `startDatetime`) for urgency context.

### Overdue Rentals (`/overdue-rentals`)

Receipts with `status = GIVEN` and `endDatetime < now`, sorted earliest-due first. Overdue duration is calculated to the nearest hour/day using the existing `DateTimeUtil.calculateOverdueHours()`.

---

## Backend — Key Files

```
backend/src/main/java/com/fashionrental/reporting/
  ReportingService.java                — all 4 report methods, @Transactional(readOnly=true)
  ReportingController.java             — 4 GET endpoints
  model/response/
    DailyRevenueResponse.java          — date + 8 financial fields
    DailyRevenueSummary.java           — per-day entry for monthly chart
    MonthlyRevenueResponse.java        — monthly totals + List<DailyRevenueSummary>
    OutstandingDepositsResponse.java   — total + List<OutstandingDepositItem>
    OutstandingDepositItem.java        — per-receipt: customer, items, deposit, daysSinceRented
    OverdueRentalsResponse.java        — count + List<OverdueRentalItem>
    OverdueRentalItem.java             — per-receipt: customer, items, endDatetime, overdueHours

backend/src/main/java/com/fashionrental/receipt/ReceiptRepository.java
  + findByCreatedAtBetweenOrderByCreatedAtAsc(from, to)

backend/src/main/java/com/fashionrental/invoice/InvoiceRepository.java
  + findByCreatedAtBetweenOrderByCreatedAtAsc(from, to)
```

No Flyway migrations — reporting is read-only over existing tables.

---

## Frontend — Key Files

```
frontend/src/
  types/reports.ts                     — DailyRevenue, MonthlyRevenue, OutstandingDeposits, OverdueRentals types
  api/reports.ts                       — getDailyRevenue, getMonthlyRevenue, getOutstandingDeposits, getOverdueRentals
  pages/reports/ReportsPage.tsx        — 4-tab reports page
```

Route: `/reports` → `ReportsPage` (previously a placeholder)

Dependencies added: `recharts` (bar chart for monthly view)

---

## ReportsPage — Tab Layout

### Monthly Revenue (default tab)
- Month picker (defaults to current month)
- 6 stat cards: Rent Collected, Deposits Collected, Deposits Refunded, Late Fee Income, Damage Income, Net Cash Flow (green/red)
- Stacked bar chart (recharts): X = day of month, stacked bars = Rent (green) + Deposit In (blue) + Late Fee + Damage (orange)
- Y-axis formatted as `₹Xk`

### Daily Revenue
- Date picker (defaults to today)
- Card with two sections: "New Rentals" (rent + deposits) and "Returns Processed" (refunds + late fee + damage)
- Net Cash Flow callout (green if positive, red if negative)

### Outstanding Deposits
- Blue callout showing total outstanding deposit liability
- Per-receipt cards with customer name/phone, item list, due date, days since rented, Process Return button

### Overdue Rentals
- Red summary banner with overdue count (or green "No overdue rentals")
- Per-receipt cards with overdue duration (e.g. "2d 3h"), customer phone, Process Return button (red)

---

## Also Shipped in This Branch

### GitHub Actions CI Workflow (`.github/workflows/ci.yml`)
- **Backend job**: Java 21 (Temurin), Gradle cache, `./gradlew test`, `./gradlew bootJar`, `docker build` to validate image
- **Frontend job**: Node 20, pnpm, `pnpm install --frozen-lockfile`, `pnpm type-check`, `pnpm lint`, `pnpm build`
- Both jobs run in parallel on push to `main`/`feature/**` and on PRs to `main`
- Test reports uploaded as artifacts on backend job (even on failure)
