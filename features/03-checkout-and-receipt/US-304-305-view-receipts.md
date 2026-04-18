# US-304 / US-305: View Active and Overdue Receipts

**Epic:** Checkout & Receipt Management
**Priority:** P0
**Depends On:** US-301 (receipts must exist)
**Blocks:** US-401 (return is initiated from receipt list)

---

## User Stories

**US-304:** View all active (not yet returned) receipts, so I know which items are currently out on rental and when they are due back.
**US-305:** View all overdue rentals (past end datetime, still GIVEN), so I can follow up with customers.

---

## Acceptance Criteria

- [ ] Active receipts list shows: receipt number, customer name, items rented, end datetime, days until due
- [ ] Overdue receipts are highlighted in red; overdue duration is shown (e.g., "2 days, 3 hrs overdue")
- [ ] List is sortable by end datetime (soonest due first by default)
- [ ] Clicking a receipt opens the full receipt detail
- [ ] Customer phone number visible so staff can call directly from the list
- [ ] A dedicated "Overdue" tab/filter shows only overdue receipts

---

## Backend Implementation

### Repository Queries

```java
// Active receipts (status = GIVEN)
List<Receipt> findByStatusOrderByEndDatetimeAsc(Receipt.Status status);

// Overdue: GIVEN and end_datetime < now
@Query("""
    SELECT r FROM Receipt r
    WHERE r.status = 'GIVEN' AND r.endDatetime < :now
    ORDER BY r.endDatetime ASC
    """)
List<Receipt> findOverdue(@Param("now") OffsetDateTime now);
```

### DTO: `ReceiptSummaryResponse.java`

```java
public record ReceiptSummaryResponse(
    UUID id,
    String receiptNumber,
    String customerName,
    String customerPhone,
    List<String> itemNames,        // ["Blue Sherwani ×2", "Gold Necklace ×1"]
    OffsetDateTime startDatetime,
    OffsetDateTime endDatetime,
    int rentalDays,
    int totalRentPaise,
    int totalDepositPaise,
    int grandTotalPaise,
    String status,
    boolean isOverdue,
    Double overdueHours            // null if not overdue
) {}
```

### API Endpoints

```
GET /api/receipts?status=GIVEN&overdue=false    → active, not overdue (sorted by endDatetime ASC)
GET /api/receipts?status=GIVEN&overdue=true     → overdue only
GET /api/receipts?status=GIVEN                  → all active (overdue + non-overdue)
GET /api/receipts?customerId={uuid}             → all receipts for a customer

Response 200:
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "receiptNumber": "R-20260418-003",
      "customerName": "Ramesh Patel",
      "customerPhone": "9876543210",
      "itemNames": ["Blue Sherwani ×2", "Gold Necklace ×1"],
      "startDatetime": "2026-04-18T10:00:00+05:30",
      "endDatetime": "2026-04-20T10:00:00+05:30",
      "rentalDays": 2,
      "totalRentPaise": 90000,
      "totalDepositPaise": 250000,
      "grandTotalPaise": 340000,
      "status": "GIVEN",
      "isOverdue": true,
      "overdueHours": 27.5
    }
  ]
}
```

---

## Frontend Implementation

### Active Receipts Page

```
[All Active]  [Overdue (3)]           ← tabs

Sort by: [Due Date ↑]

R-20260418-003                                    [Process Return]
Ramesh Patel · 📱 9876543210
Blue Sherwani ×2, Gold Necklace ×1
Apr 18 10:00 → Apr 20 10:00 (2 days)
Grand Total: ₹3,400
─────────────────────────────────────────── ← red row if overdue
R-20260415-001   🔴 OVERDUE: 27 hrs 30 min [Process Return]
Priya Shah · 📱 9988776655
Red Lehenga ×1
Apr 15 10:00 → Apr 17 10:00 (2 days)
Grand Total: ₹1,500
```

**Overdue duration display:**
```ts
function formatOverdue(hours: number): string {
  if (hours < 1) return `${Math.round(hours * 60)} min overdue`
  if (hours < 24) return `${Math.floor(hours)} hr ${Math.round((hours % 1) * 60)} min overdue`
  const days = Math.floor(hours / 24)
  const remainHrs = Math.floor(hours % 24)
  return `${days} day${days > 1 ? 's' : ''} ${remainHrs} hr overdue`
}
```

---

## Test Cases

| Scenario | Expected |
|----------|----------|
| Receipt past end_datetime by 3 hours | isOverdue=true, overdueHours=3.0 |
| Receipt due tomorrow | isOverdue=false, shown in "All Active" tab |
| Receipt exactly at end_datetime | isOverdue=false (no late fee applies at 0 hours) |
| No active receipts | Empty state: "No active rentals" |
| 5 overdue receipts | Overdue tab badge shows "(5)" |
