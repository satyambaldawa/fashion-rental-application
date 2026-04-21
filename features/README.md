# Features Directory

Implementation-ready story files for the Fashion Rental Management Application MVP.

Each file contains everything a developer needs to implement the feature end-to-end: user story, acceptance criteria, backend entity/service/repository/controller code, API endpoints with request/response shapes, frontend components, and test cases with worked examples.

---

## Status Legend

| Symbol | Meaning |
|--------|---------|
| ✅ | Complete — all acceptance criteria verified |
| 🚧 | In Progress |
| ⏳ | Pending |

---

## Story Index

### 00 — Project Setup

| File | Description | Priority | Status |
|------|-------------|----------|--------|
| `01-spring-boot-scaffold.md` | Spring Boot app, Flyway schema, exception handling, DateTimeUtil | P0 | ✅ |
| `02-react-pwa-scaffold.md` | React PWA, routing, API client, auth flow, currency utils | P0 | ✅ |

### 01 — Inventory Management

| File | Story | Priority | Status |
|------|-------|----------|--------|
| `US-101-browse-inventory.md` | Browse all items with availability | P0 | ✅ |
| `US-102-search-filter-items.md` | Search by name, filter by category/size | P0 | ✅ |
| `US-103-availability-check.md` | Check available units for a date range | P0 | ✅ |
| `US-104-add-item.md` | Add new item to catalog | P0 | ✅ |
| `US-105-edit-item.md` | Edit item details | P1 | ⏳ |
| `US-106-adjust-unit-count.md` | Increase/decrease unit count | P1 | ⏳ |
| `US-107-item-photo-upload.md` | Upload photos to Cloudflare R2 | P0 | ✅ |

### 02 — Customer Management

| File | Story | Priority | Status |
|------|-------|----------|--------|
| `US-201-register-customer.md` | Register new customer | P0 | ⏳ |
| `US-202-search-customer.md` | Search customer by phone or name | P0 | ⏳ |
| `US-203-customer-rental-history.md` | View rental history | P1 | ⏳ |
| `US-204-edit-customer.md` | Edit customer contact details | P1 | ⏳ |

### 03 — Checkout & Receipt

| File | Story | Priority | Status |
|------|-------|----------|--------|
| `US-301-303-create-receipt.md` | Full checkout flow: select customer, items, dates; calculate billing; create receipt | P0 | ⏳ |
| `US-304-305-view-receipts.md` | View active and overdue receipts | P0 | ⏳ |
| `US-502-availability-guard.md` | Prevent double-booking (embedded in checkout) | P0 | ⏳ |

### 04 — Return & Invoice

| File | Story | Priority | Status |
|------|-------|----------|--------|
| `US-401-404-process-return.md` | Process return: late fees + damage + deposit refund + invoice generation | P0 | ⏳ |

### 05 — Configuration

| File | Story | Priority | Status |
|------|-------|----------|--------|
| `US-601-602-configuration.md` | Configure late fee tiers and deposit amounts | P0 | ✅ |

### 06 — Reporting

| File | Story | Priority | Status |
|------|-------|----------|--------|
| `US-701-703-reports.md` | Daily revenue, outstanding deposits, overdue rentals | P0 | ⏳ |

---

## Key Technical Decisions (Quick Reference)

| Decision | Choice |
|----------|--------|
| All monetary amounts | Stored as **whole rupees (INTEGER)**. ₹150 = `150`. No paise conversion. |
| Rental day calculation | `(end_datetime - start_datetime)` in 24h increments. Minimum 1 day |
| Late fee calculation | Overdue hours = `return_datetime - end_datetime`. Match against LateFeeRule tiers |
| Availability | `item.quantity - SUM(rli.quantity WHERE receipt.status=GIVEN AND dates overlap)` |
| Image storage | Cloudflare R2. Two files: full (1200px WebP) + thumbnail (300px WebP) |
| PO persistence | **Not persisted.** Preview is stateless (`POST /api/checkout/preview`). Receipt created atomically |
| Customer PK | UUID (not phone). Phone is `UNIQUE NOT NULL` with index |
| Receipt/Invoice | **Two separate entities.** Receipt = rental agreement. Invoice = return settlement |
| Datetime fields | All `TIMESTAMP WITH TIME ZONE`. API accepts/returns ISO 8601 with timezone offset |

---

## Suggested Build Order

1. `00-project-setup` — both files
2. `05-configuration` (US-601) — seeds late fee rules on startup, needed for return flow
3. `01-inventory-management` (US-101 → US-104 → US-103 → US-107) — P0 first
4. `02-customer-management` (US-201 → US-202) — P0 first
5. `03-checkout-and-receipt` (US-301 → US-304) — depends on 01 + 02
6. `04-return-and-invoice` (US-401) — depends on 03 + 05
7. `06-reporting` (US-701 → US-703) — read-only, build last

P1 stories (US-105, US-106, US-203, US-204) can be done after the P0 set is deployed and working.
