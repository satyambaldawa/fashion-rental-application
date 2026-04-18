# US-104: Add New Item to Inventory

**Epic:** Inventory Management
**Priority:** P0
**Depends On:** SETUP-01, SETUP-02, US-107 (photo upload — can be done post-save)
**Blocks:** Nothing (but needed before any rental can be created)

---

## User Story

> As a Shop Owner, I want to add a new item to the inventory catalog, so that newly purchased stock is immediately available for rental.

---

## Acceptance Criteria

- [ ] Required fields: name, category, daily rate (₹), deposit amount (₹), quantity
- [ ] Optional fields: size, description, notes
- [ ] System assigns UUID automatically; item is immediately available for booking after save
- [ ] Photos can be uploaded after the item is created (via US-107)
- [ ] Rate and deposit are entered in ₹ by staff; stored as paise internally
- [ ] Quantity must be ≥ 1
- [ ] Rate must be > 0; deposit must be ≥ 0

---

## Backend Implementation

### DTO: `CreateItemRequest.java`

```java
public record CreateItemRequest(
    @NotBlank String name,
    @NotNull Item.Category category,
    String size,
    String description,
    @NotNull @Min(1) Integer ratePaise,        // Staff enters in ₹, frontend converts to paise
    @NotNull @Min(0) Integer depositPaise,
    @NotNull @Min(1) Integer quantity,
    String notes
) {}
```

### Service: `ItemService.createItem()`

```java
@Transactional
public ItemDetailResponse createItem(CreateItemRequest request) {
    Item item = new Item();
    item.setName(request.name());
    item.setCategory(request.category());
    item.setSize(request.size());
    item.setDescription(request.description());
    item.setRatePaise(request.ratePaise());
    item.setDepositPaise(request.depositPaise());
    item.setQuantity(request.quantity());
    item.setNotes(request.notes());
    item.setIsActive(true);
    item.setCreatedAt(OffsetDateTime.now());
    item.setUpdatedAt(OffsetDateTime.now());

    Item saved = itemRepository.save(item);
    return toDetailResponse(saved);
}
```

### API Endpoint

```
POST /api/items
Content-Type: application/json
Authorization: Bearer {token}

Body:
{
  "name": "Blue Sherwani",
  "category": "COSTUME",
  "size": "M",
  "description": "Traditional blue sherwani with golden border",
  "ratePaise": 20000,       // ₹200/day
  "depositPaise": 100000,   // ₹1,000
  "quantity": 3,
  "notes": "Purchased April 2026"
}

Response 201:
{
  "success": true,
  "data": {
    "id": "generated-uuid",
    "name": "Blue Sherwani",
    "category": "COSTUME",
    "size": "M",
    "ratePaise": 20000,
    "depositPaise": 100000,
    "quantity": 3,
    "isActive": true,
    "photos": [],
    "createdAt": "2026-04-18T10:30:00+05:30"
  }
}
```

---

## Frontend Implementation

### Page: `src/pages/inventory/AddItemPage.tsx`

Use Ant Design `Form` with the following fields:

```
Item Name *         [Input]
Category *          [Select: Costume / Accessories / Pagdi / Dress / Ornaments]
Size                [Input: e.g., S, M, L, 38, Free Size]
Daily Rate (₹) *    [InputNumber, min=1]
Deposit (₹) *       [InputNumber, min=0]
Quantity *          [InputNumber, min=1, default=1]
Description         [TextArea]
Notes               [TextArea]
                    [Save Item] button
```

**Currency handling:**
- Staff enters rate and deposit in ₹ (e.g., 200)
- Before sending to API: multiply by 100 → send as paise (e.g., 20000)

```ts
const onSubmit = (values: FormValues) => {
  const payload = {
    ...values,
    ratePaise: rupeesToPaise(values.rateRupees),
    depositPaise: rupeesToPaise(values.depositRupees),
  }
  createItem(payload)
}
```

**After save:** Show success message → navigate to the new item's detail page → staff can upload photos there.

---

## Test Cases

| Scenario | Expected |
|----------|----------|
| All required fields filled | Item created, 201 returned, item appears in inventory |
| Name is blank | 400 validation error |
| Rate = 0 | 400 validation error ("Rate must be at least ₹1") |
| Deposit = 0 | Allowed (some items may have zero deposit) |
| Quantity = 0 | 400 validation error |
| Quantity = 5 | Item created with quantity 5, all 5 immediately available |
| Category not in enum | 400 validation error |
