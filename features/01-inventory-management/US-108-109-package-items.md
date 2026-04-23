# US-108 / US-109: Package / Group Offering Support

**Epic:** Inventory Management  
**Priority:** P0  
**Depends On:** US-101, US-103, US-104, US-301  
**Blocks:** Nothing

---

## User Stories

**US-108:** As staff, I want to create a "package" item that bundles multiple individual items into one offering, so I can rent out costume sets as a single unit with one price.

**US-109:** As staff, I want to add a package to the checkout cart and have the system automatically reserve all component items, so components can't be double-booked as individual rentals during an active package rental.

---

## Acceptance Criteria

**US-108 — Create Package Item:**
- [x] Staff can toggle item type between INDIVIDUAL and PACKAGE when adding an item
- [x] For PACKAGE: staff searches and selects component INDIVIDUAL items with a quantity-per-set
- [x] At least one component is required to save a package
- [x] Only INDIVIDUAL items can be added as components (not other packages)
- [x] Package is saved with rate, deposit, quantity (number of complete sets), and component list
- [x] Item detail drawer shows a "Package" badge and lists all components
- [x] Inventory list shows packages with a distinguishing badge

**US-109 — Package in Checkout:**
- [x] Package availability = min(package stock available, floor(each component's available ÷ qty-per-set))
- [x] Checkout preview shows the package line item (billed) and components as "Includes" info (not separately billed)
- [x] On receipt creation: package is added as one billed line item; each component is added as a ₹0 / ₹0 reservation line item
- [x] Component line items on the receipt reserve inventory so individual bookings are blocked for those dates
- [x] Return flow works unchanged (all line items returned together via the receipt)

---

## Domain Rules

```
Package availability for date range:
  avail = package.quantity - bookedPackageUnits(dateRange)
  for each component c (qty_per_set units required per set):
      componentCapacity = floor(availableUnits(c, dateRange) / c.qty_per_set)
      avail = min(avail, componentCapacity)
  return max(0, avail)

Receipt line items when package ×N is rented:
  → package line item: quantity=N, rateSnapshot=package.rate, depositSnapshot=package.deposit, lineRent=rate×days×N, lineDeposit=deposit×N
  → per component c: quantity=c.qty_per_set×N, rateSnapshot=0, depositSnapshot=0, lineRent=0, lineDeposit=0
```

---

## Backend Implementation

### PackageComponent Entity

```java
// backend/src/main/java/com/fashionrental/inventory/PackageComponent.java
@Entity
@Table(name = "package_components")
public class PackageComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "package_item_id", nullable = false)
    private Item packageItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_item_id", nullable = false)
    private Item componentItem;

    @Column(nullable = false)
    private int quantity = 1;

    // getters/setters
}
```

### PackageComponentRepository

```java
public interface PackageComponentRepository extends JpaRepository<PackageComponent, UUID> {
    List<PackageComponent> findByPackageItem_Id(UUID packageItemId);
    void deleteByPackageItem_Id(UUID packageItemId);
}
```

### Update Item entity

Add to `Item.java`:
```java
@OneToMany(mappedBy = "packageItem", fetch = FetchType.LAZY,
           cascade = CascadeType.ALL, orphanRemoval = true)
private List<PackageComponent> packageComponents = new ArrayList<>();

public List<PackageComponent> getPackageComponents() { return packageComponents; }
```

### Updated CreateItemRequest

```java
public record CreateItemRequest(
    @NotBlank String name,
    @NotNull Category category,
    @NotNull ItemType itemType,
    String size,
    String description,
    @NotNull @Min(1) Integer rate,
    @NotNull @Min(0) Integer deposit,
    @NotNull @Min(1) Integer quantity,
    String notes,
    @Min(0) Integer purchaseRate,
    String vendorName,
    List<PackageComponentRequest> components   // required when itemType = PACKAGE, null/empty for INDIVIDUAL
) {}

public record PackageComponentRequest(
    @NotNull UUID componentItemId,
    @NotNull @Min(1) Integer quantity
) {}
```

### Updated ItemService

`createItem()` changes:
```java
if (request.itemType() == Item.ItemType.PACKAGE) {
    if (request.components() == null || request.components().isEmpty()) {
        throw new ValidationException("A package must have at least one component item.");
    }
    for (PackageComponentRequest comp : request.components()) {
        Item componentItem = itemRepository.findById(comp.componentItemId())
            .orElseThrow(() -> new ResourceNotFoundException("Component item not found: " + comp.componentItemId()));
        if (componentItem.getItemType() != Item.ItemType.INDIVIDUAL) {
            throw new ValidationException("Only INDIVIDUAL items can be added as package components.");
        }
        PackageComponent pc = new PackageComponent();
        pc.setPackageItem(saved);
        pc.setComponentItem(componentItem);
        pc.setQuantity(comp.quantity());
        packageComponentRepository.save(pc);
    }
}
```

### Updated ItemDetailResponse

```java
public record ItemDetailResponse(
    UUID id,
    String name,
    Category category,
    ItemType itemType,
    String size,
    String description,
    int rate,
    int deposit,
    int quantity,
    boolean isActive,
    String notes,
    Integer purchaseRate,
    String vendorName,
    List<PackageComponentResponse> components,  // null for INDIVIDUAL
    List<ItemPhotoResponse> photos
) {}

public record PackageComponentResponse(
    UUID componentItemId,
    String componentItemName,
    String componentItemCategory,
    int quantity
) {}
```

### Updated AvailabilityService

```java
public int getAvailableQuantity(UUID itemId, OffsetDateTime start, OffsetDateTime end) {
    Item item = itemRepository.findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + itemId));

    if (!item.getIsActive()) return 0;

    int booked = (start == null || end == null)
            ? itemRepository.countCurrentlyBookedUnits(itemId)
            : itemRepository.countBookedUnits(itemId, start, end);

    int available = Math.max(0, item.getQuantity() - booked);

    // For packages: further constrain by component availability
    if (item.getItemType() == Item.ItemType.PACKAGE) {
        List<PackageComponent> components = packageComponentRepository.findByPackageItem_Id(itemId);
        for (PackageComponent comp : components) {
            int compAvailable = getAvailableQuantity(comp.getComponentItem().getId(), start, end);
            int setsFromComp = compAvailable / comp.getQuantity();
            available = Math.min(available, setsFromComp);
        }
    }

    return available;
}
```

### Updated CheckoutService — preview()

```java
// For PACKAGE items: also return the component names as informational data
// (components are not separately billed — just shown in UI)
// Available quantity already accounts for component constraints via AvailabilityService
```

The preview line item for a package shows `availableQuantity` already correctly constrained.
Add `List<String> componentNames` to `PreviewLineItem` (null for INDIVIDUAL):

```java
public record PreviewLineItem(
    UUID itemId,
    String itemName,
    Item.ItemType itemType,
    int rate,
    int deposit,
    int quantity,
    int rentalDays,
    int lineRent,
    int lineDeposit,
    int availableQuantity,
    List<String> componentSummary   // e.g. ["Sherwani ×1", "Pagdi ×1"] for packages; null for individuals
) {}
```

### Updated CheckoutService — createReceipt()

```java
for (var lineItemRequest : request.items()) {
    Item item = itemRepository.findById(lineItemRequest.itemId()) ...

    // Availability re-check (existing guard)
    int available = availabilityService.getAvailableQuantity(...);
    if (available < lineItemRequest.quantity()) throw new ConflictException(...);

    // Package: billed line item for the package itself
    ReceiptLineItem packageLine = buildLineItem(receipt, item,
        lineItemRequest.quantity(),
        item.getRate(), item.getDeposit(), rentalDays);
    lineItems.add(packageLine);
    totalRent    += packageLine.getLineRent();
    totalDeposit += packageLine.getLineDeposit();

    // Package: zero-rate reservation lines for each component
    if (item.getItemType() == Item.ItemType.PACKAGE) {
        List<PackageComponent> components = packageComponentRepository.findByPackageItem_Id(item.getId());
        for (PackageComponent comp : components) {
            int reserveQty = comp.getQuantity() * lineItemRequest.quantity();
            lineItems.add(buildLineItem(receipt, comp.getComponentItem(), reserveQty, 0, 0, rentalDays));
        }
    }
}

private ReceiptLineItem buildLineItem(Receipt receipt, Item item, int qty, int rate, int deposit, int days) {
    ReceiptLineItem li = new ReceiptLineItem();
    li.setReceipt(receipt);
    li.setItem(item);
    li.setQuantity(qty);
    li.setRateSnapshot(rate);
    li.setDepositSnapshot(deposit);
    li.setLineRent(rate * days * qty);
    li.setLineDeposit(deposit * qty);
    return li;
}
```

### API Endpoints (no new endpoints needed)

Existing endpoints handle everything:
- `POST /api/items` — accepts `components` array when `itemType=PACKAGE`
- `GET /api/items/{id}` — returns `components` in the detail response
- `POST /api/checkout/preview` — works unchanged (availability already constrains by components)
- `POST /api/checkout` — works unchanged (creates component reservation lines internally)

---

## Frontend Implementation

### Types update (`src/types/inventory.ts`)

```ts
export interface PackageComponentRequest {
  componentItemId: string
  quantity: number
}

// Update CreateItemRequest:
export interface CreateItemRequest {
  name: string
  category: string
  itemType: 'INDIVIDUAL' | 'PACKAGE'
  size?: string
  description?: string
  rate: number
  deposit: number
  quantity: number
  notes?: string
  purchaseRate?: number
  vendorName?: string
  components?: PackageComponentRequest[]   // PACKAGE only
}

export interface PackageComponent {
  componentItemId: string
  componentItemName: string
  componentItemCategory: string
  quantity: number
}

// Update ItemDetail:
export interface ItemDetail {
  ...existing fields...
  itemType: 'INDIVIDUAL' | 'PACKAGE'
  components: PackageComponent[] | null
}
```

### AddItemPage — Package mode

- Toggle at top: `<Radio.Group>` with INDIVIDUAL / PACKAGE
- When PACKAGE: hide size field; show "Package Components" section
  - Search box: calls `GET /api/items?search=...&type=INDIVIDUAL` (add `type` filter if needed, or filter client-side)
  - Found items appear as selectable rows; staff sets quantity per set
  - Added components listed below search with remove button
  - Minimum 1 component validation on submit

### ItemDetailDrawer — Package display

- Show `<Tag color="purple">Package</Tag>` next to item name when `itemType === 'PACKAGE'`
- Below the Descriptions section: "Components" sub-section listing each component with quantity

### CheckoutPage — Cart display

In the cart, when an item is a package (detect via `itemType` on the item detail, or via `componentSummary` in preview response):
- Show the package name as usual
- Show "Includes: Sherwani ×1, Pagdi ×1, ..." as secondary text below the item name

---

## Test Cases

| Scenario | Expected |
|----------|----------|
| Create package with 0 components | ValidationException — at least one component required |
| Create package with another package as component | ValidationException — only INDIVIDUAL items allowed |
| Package available = 3; component A available = 2 (qty_per_set=1) | Package shows available = 2 |
| Package available = 3; component A available = 6 (qty_per_set=3) | Package shows available = 2 |
| Checkout package ×1; receipt line items | 1 billed package line + N zero-rate component lines |
| Component rented individually, filling all stock | Package shows available = 0 |
| Package rented, filling component stock | Component shows reduced availability for that range |

---

## Key Decisions

- **No new API endpoints**: package components are managed entirely through the existing item create/detail endpoints. The component reservation logic is internal to `CheckoutService`.
- **Components not shown on invoice**: Component line items (₹0) appear on the receipt for inventory tracking but are not shown on the customer-facing invoice breakdown (only the package line item is). The existing `InvoiceDetailPage` already hides zero-value lines via the UI (or we filter by `rateSnapshot > 0`).
- **Return works as-is**: All line items (package + components) are returned together when the receipt is marked RETURNED. No changes needed to `ReturnService`.
