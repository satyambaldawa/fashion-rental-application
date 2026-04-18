# US-103: Check Item Availability for a Date Range

**Epic:** Inventory Management
**Priority:** P0
**Depends On:** US-101
**Blocks:** US-301 (checkout must check availability), US-502 (availability guard)

---

## User Story

> As a Shop Staff member, I want to check how many units of a specific item are available for a specific date/time range, so that I can confirm availability before creating a rental receipt.

---

## Acceptance Criteria

- [ ] Given an item ID, start datetime, and end datetime, the system returns the number of available units
- [ ] A unit is unavailable if it is linked to a receipt with status `GIVEN` whose datetime range **overlaps** the requested range
- [ ] If available = 0, the response says "0 available" and the item cannot be added to an order (enforced in US-301/502)
- [ ] Availability is real-time — a unit returned moments ago (status changed to RETURNED) is immediately available

---

## Backend Implementation

### Availability Query

The core query: available units = total quantity minus units currently booked in overlapping GIVEN receipts.

```java
// src/inventory/repository/ItemRepository.java

@Query("""
    SELECT COALESCE(SUM(rli.quantity), 0)
    FROM ReceiptLineItem rli
    JOIN rli.receipt r
    WHERE rli.item.id = :itemId
      AND r.status = 'GIVEN'
      AND r.startDatetime < :endDatetime
      AND r.endDatetime > :startDatetime
    """)
Integer countBookedUnits(
    @Param("itemId") UUID itemId,
    @Param("startDatetime") OffsetDateTime startDatetime,
    @Param("endDatetime") OffsetDateTime endDatetime
);
```

**Overlap condition explained:**
Two date ranges [A, B] and [C, D] overlap when: `A < D AND B > C`
- Booking starts before requested end AND booking ends after requested start
- This correctly handles: fully inside, partially overlapping, surrounding

### AvailabilityService.java

```java
@Service
@Transactional(readOnly = true)
public class AvailabilityService {

    public int getAvailableQuantity(UUID itemId, OffsetDateTime start, OffsetDateTime end) {
        Item item = itemRepository.findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + itemId));

        if (!item.getIsActive()) return 0;
        if (start == null || end == null) {
            // General availability (no date range) — used in browse view
            int booked = itemRepository.countCurrentlyBookedUnits(itemId);
            return Math.max(0, item.getQuantity() - booked);
        }

        int booked = itemRepository.countBookedUnits(itemId, start, end);
        return Math.max(0, item.getQuantity() - booked);
    }

    /**
     * Check multiple items at once — used in checkout preview (US-302)
     */
    public Map<UUID, Integer> getAvailableQuantities(
        List<UUID> itemIds,
        OffsetDateTime start,
        OffsetDateTime end
    ) {
        return itemIds.stream().collect(Collectors.toMap(
            id -> id,
            id -> getAvailableQuantity(id, start, end)
        ));
    }
}
```

### Controller Endpoint

```java
@GetMapping("/{id}/availability")
public ResponseEntity<ApiResponse<AvailabilityResponse>> checkAvailability(
    @PathVariable UUID id,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDatetime,
    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDatetime
) {
    if (!endDatetime.isAfter(startDatetime)) {
        throw new ValidationException("endDatetime must be after startDatetime");
    }
    int available = availabilityService.getAvailableQuantity(id, startDatetime, endDatetime);
    return ResponseEntity.ok(ApiResponse.ok(new AvailabilityResponse(id, available, available > 0)));
}
```

### DTO

```java
public record AvailabilityResponse(UUID itemId, int availableQuantity, boolean isAvailable) {}
```

### API Endpoint

```
GET /api/items/{id}/availability?startDatetime=2026-04-20T10:00:00+05:30&endDatetime=2026-04-22T10:00:00+05:30

Response 200:
{
  "success": true,
  "data": {
    "itemId": "uuid",
    "availableQuantity": 2,
    "isAvailable": true
  }
}
```

---

## Frontend Implementation

This endpoint is called:
1. From the **item detail drawer** (US-101) — staff manually checks before choosing
2. From the **checkout page** (US-301) — when adding items to an order, availability is checked against the selected rental dates

For the inventory browse page, show an availability checker inside the item detail drawer:

```tsx
// Inside ItemDetailDrawer
const [dateRange, setDateRange] = useState<[Dayjs, Dayjs] | null>(null)

const { data: availability } = useQuery({
  queryKey: ['availability', item.id, dateRange],
  queryFn: () => itemsApi.checkAvailability(item.id, dateRange![0], dateRange![1]),
  enabled: !!dateRange,
})

// UI:
<RangePicker
  showTime={{ format: 'HH:mm' }}
  format="DD MMM YYYY HH:mm"
  onChange={(dates) => setDateRange(dates as [Dayjs, Dayjs])}
/>
{availability && (
  <Tag color={availability.isAvailable ? 'green' : 'red'}>
    {availability.availableQuantity} unit(s) available
  </Tag>
)}
```

---

## Test Cases with Worked Examples

**Setup:** Item "Blue Sherwani" has quantity = 3. Receipts:
- Receipt A: 3 units rented, Apr 18 10:00 → Apr 20 10:00, status = GIVEN
- Receipt B: 1 unit rented, Apr 22 10:00 → Apr 24 10:00, status = GIVEN

| Query Range | Booked | Available | Reason |
|-------------|--------|-----------|--------|
| Apr 19 10:00 → Apr 21 10:00 | 3 (Receipt A overlaps) | 0 | A starts before query end, ends after query start |
| Apr 20 10:00 → Apr 22 10:00 | 0 | 3 | A ends exactly at query start (no overlap: B > C fails) |
| Apr 21 10:00 → Apr 23 10:00 | 1 (Receipt B overlaps) | 2 | B starts before query end, ends after query start |
| Apr 25 10:00 → Apr 27 10:00 | 0 | 3 | No active receipts overlap |

**Same-day re-rental (OQ-13):** Item returned at 10:00 AM (Receipt A status → RETURNED). Query for 10:00 AM same day → available = 3. ✓

**Edge: endDatetime ≤ startDatetime:** API returns 400 Bad Request.
