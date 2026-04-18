# US-106: Adjust Item Unit Count

**Epic:** Inventory Management
**Priority:** P1
**Depends On:** US-104
**Blocks:** Nothing

---

## User Story

> As a Shop Owner, I want to adjust the physical unit count of an item (increase or decrease), so that I can account for new stock purchases or items retired due to irreparable damage.

---

## Acceptance Criteria

- [ ] I can increase the quantity — new units become immediately available
- [ ] I can decrease the quantity — system prevents reducing below the number of currently rented-out units
- [ ] A reason/note field is required for any adjustment
- [ ] Adjustment is immediate (no confirmation workflow)

---

## Backend Implementation

### DTO: `AdjustQuantityRequest.java`

```java
public record AdjustQuantityRequest(
    @NotNull Integer newQuantity,
    @NotBlank String reason
) {}
```

### Service

```java
@Transactional
public ItemDetailResponse adjustQuantity(UUID id, AdjustQuantityRequest request) {
    Item item = itemRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + id));

    if (request.newQuantity() < 0) {
        throw new ValidationException("Quantity cannot be negative");
    }

    // Count currently rented-out units (GIVEN receipts)
    int currentlyRented = itemRepository.countCurrentlyBookedUnits(id);
    if (request.newQuantity() < currentlyRented) {
        throw new ConflictException(
            "Cannot reduce quantity to " + request.newQuantity() +
            " — " + currentlyRented + " unit(s) are currently rented out."
        );
    }

    item.setQuantity(request.newQuantity());
    item.setUpdatedAt(OffsetDateTime.now());
    // Future: log adjustment reason to an audit table

    return toDetailResponse(itemRepository.save(item));
}
```

### Repository Query (count currently booked, any time)

```java
@Query("""
    SELECT COALESCE(SUM(rli.quantity), 0)
    FROM ReceiptLineItem rli
    JOIN rli.receipt r
    WHERE rli.item.id = :itemId AND r.status = 'GIVEN'
    """)
Integer countCurrentlyBookedUnits(@Param("itemId") UUID itemId);
```

### API Endpoint

```
PATCH /api/items/{id}/quantity
Body: { "newQuantity": 5, "reason": "Purchased 2 more units from supplier" }

Response 200: updated ItemDetailResponse
Response 409: "Cannot reduce quantity to X — Y unit(s) are currently rented out."
```

---

## Frontend Implementation

Show an "Adjust Quantity" button on the item detail page (owner view only):

```
Current quantity: 3
Currently rented: 1
Currently available: 2

New Quantity: [InputNumber]
Reason:       [TextArea, required]
              [Save Adjustment]
```

Show the conflict message clearly if the owner tries to reduce below rented count.

---

## Test Cases

| Scenario | Expected |
|----------|----------|
| Increase from 3 to 5, 1 currently rented | Allowed. Available = 4 |
| Decrease from 3 to 2, 1 currently rented | Allowed. Available = 1 |
| Decrease from 3 to 0, 1 currently rented | 409 Conflict: "1 unit(s) currently rented out" |
| Decrease from 3 to 0, 0 currently rented | Allowed (item effectively unavailable) |
| No reason provided | 400 validation error |
