# US-502: Availability Guard — Prevent Double Booking

**Epic:** Checkout & Receipt Management
**Priority:** P0
**Depends On:** US-103 (availability check), US-301 (receipt creation)
**Blocks:** Nothing (embedded in checkout flow)

---

## User Story

> As a Shop Staff member, I want the system to prevent me from booking an item for datetimes when no units are available, so that over-booking is impossible and inventory integrity is maintained.

---

## Acceptance Criteria

- [ ] When adding an item to the order, the system checks availability against the selected date range in real time
- [ ] If available quantity < requested quantity, the item cannot be added (UI blocks it with a clear message)
- [ ] At final receipt creation (POST /api/receipts), availability is rechecked atomically in a DB transaction — this is the authoritative guard
- [ ] If availability changed between preview and confirm (race condition — another receipt was just created), the API returns 409 with the specific item name
- [ ] Same-day re-rental: a unit returned earlier today is immediately available (status=RETURNED receipts are excluded from availability calculation)

---

## Implementation

This story is implemented as part of US-301 (checkout) and US-103 (availability check). No separate code needed — document this as a cross-cutting requirement.

### Where the Guard Lives

| Layer | What It Does |
|-------|-------------|
| **Frontend (soft guard)** | When staff selects dates and adds items, calls `GET /api/items/{id}/availability?startDatetime=...&endDatetime=...`. If available < requested qty, disables "Add to Order" with message: "Only X unit(s) available for this period." |
| **Preview endpoint** | `POST /api/checkout/preview` checks availability for all items and flags unavailable ones in the response. `allAvailable: false` + list of unavailable item names. Frontend shows this before confirming. |
| **Receipt creation (hard guard)** | `POST /api/receipts` rechecks inside a `@Transactional` block. If any item is short, throws `ConflictException` (409). This is the definitive guard — the only guarantee. |

### Availability Query (reminder)

Two ranges [start, end] overlap when: `start < end2 AND end > start2`

```sql
SELECT COALESCE(SUM(rli.quantity), 0)
FROM receipt_line_items rli
JOIN receipts r ON rli.receipt_id = r.id
WHERE rli.item_id = :itemId
  AND r.status = 'GIVEN'                    -- only GIVEN (not RETURNED)
  AND r.start_datetime < :endDatetime        -- overlapping
  AND r.end_datetime > :startDatetime
```

`available = item.quantity - bookedCount`

---

## Test Cases

| Scenario | Expected |
|----------|----------|
| Item qty=3, 2 booked, request 2 | Allowed (3-2=1 available, but requesting 2: 409). Correction: 3 booked = 3, request 2 → 409 |
| Item qty=3, 2 booked, request 1 | Allowed (1 available) |
| Item returned at 10am, new booking from 10am same day | Available (RETURNED receipts excluded from count) |
| Two staff submit at same moment for the last unit | One succeeds (gets the unit), other gets 409 (transaction isolation handles this) |
| Dates don't overlap (e.g., returning Apr 20, new booking Apr 21) | Available — no overlap |
