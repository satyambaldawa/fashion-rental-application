# US-105: Edit Item Details

**Epic:** Inventory Management
**Priority:** P1
**Depends On:** US-104
**Blocks:** Nothing

---

## User Story

> As a Shop Owner, I want to edit an existing item's details (rate, deposit, description, photos), so that the catalog stays accurate when pricing or item details change.

---

## Acceptance Criteria

- [ ] All fields editable: name, category, size, rate, deposit, description, notes
- [ ] Editing the rate does **NOT** change rates on existing active receipts (snapshot pricing protects historical data)
- [ ] `updated_at` timestamp is updated on save
- [ ] Photos are managed separately via US-107 (upload) — not part of this form

---

## Backend Implementation

### DTO: `UpdateItemRequest.java`

```java
public record UpdateItemRequest(
    @NotBlank String name,
    @NotNull Item.Category category,
    String size,
    String description,
    @NotNull @Min(1) Integer ratePaise,
    @NotNull @Min(0) Integer depositPaise,
    String notes
) {}
```

**Note:** `quantity` is NOT editable here — use US-106 (Adjust Unit Count) for that. `is_active` is managed via archive/unarchive (separate action, not this form).

### Service

```java
@Transactional
public ItemDetailResponse updateItem(UUID id, UpdateItemRequest request) {
    Item item = itemRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + id));

    item.setName(request.name());
    item.setCategory(request.category());
    item.setSize(request.size());
    item.setDescription(request.description());
    item.setRatePaise(request.ratePaise());
    item.setDepositPaise(request.depositPaise());
    item.setNotes(request.notes());
    item.setUpdatedAt(OffsetDateTime.now());

    // Note: existing RECEIPT_LINE_ITEMS have rate_snapshot_paise — they are NOT affected.
    // Only new receipts created after this update will use the new rate.

    return toDetailResponse(itemRepository.save(item));
}
```

### API Endpoint

```
PUT /api/items/{id}
Body: same shape as CreateItemRequest minus quantity

Response 200: updated ItemDetailResponse
Response 404: item not found
Response 400: validation error
```

---

## Frontend Implementation

Reuse the same form as AddItemPage, pre-populated with current values.

```tsx
// Pre-populate form:
form.setFieldsValue({
  name: item.name,
  category: item.category,
  size: item.size,
  rateRupees: paisaToRupees(item.ratePaise),
  depositRupees: paisaToRupees(item.depositPaise),
  description: item.description,
  notes: item.notes,
})
```

Show a warning banner above the rate field:
> "Changing the daily rate only affects new rentals. Existing active receipts keep their original rate."

---

## Test Cases

| Scenario | Expected |
|----------|----------|
| Change rate from ₹200 to ₹250 | New receipts use ₹250; existing receipt_line_items keep rate_snapshot_paise = 20000 |
| Change category | Saved immediately |
| Save with blank name | 400 validation error |
| Item not found | 404 response |
