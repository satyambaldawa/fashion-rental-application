# US-101: Browse Inventory

**Epic:** Inventory Management
**Priority:** P0
**Depends On:** SETUP-01, SETUP-02
**Blocks:** US-301 (item selection in checkout)

---

## User Story

> As a Shop Staff member, I want to browse all items in the inventory with their details (name, size, category, rate, availability), so that I can quickly identify suitable items for a customer.

---

## Acceptance Criteria

- [ ] Opening the inventory view shows all active items with: name, category, size, daily rate (in ₹), current available quantity, and primary photo thumbnail
- [ ] Items with zero available units are visually distinct (greyed out / "Unavailable" badge)
- [ ] Items with `is_active = false` are NOT shown to staff (only to owner in item management)
- [ ] Items load in paginated batches (20 per page) without full-page reload
- [ ] Page title shows total count: "Inventory (142 items)"

---

## Backend Implementation

### Entity: `Item.java`

```java
@Entity
@Table(name = "items")
public class Item {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    private String size;
    private String description;

    @Column(name = "rate_paise", nullable = false)
    private Integer ratePaise;

    @Column(name = "deposit_paise", nullable = false)
    private Integer depositPaise;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "item", fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    private List<ItemPhoto> photos = new ArrayList<>();

    public enum Category {
        COSTUME, ACCESSORIES, PAGDI, DRESS, ORNAMENTS
    }
}
```

### Repository: `ItemRepository.java`

```java
public interface ItemRepository extends JpaRepository<Item, UUID> {

    Page<Item> findByIsActiveTrueOrderByNameAsc(Pageable pageable);

    // Used for search (US-102)
    @Query("""
        SELECT i FROM Item i
        WHERE i.isActive = true
        AND (:search IS NULL OR LOWER(i.name) LIKE LOWER(CONCAT('%', :search, '%')))
        AND (:category IS NULL OR i.category = :category)
        AND (:size IS NULL OR LOWER(i.size) LIKE LOWER(CONCAT('%', :size, '%')))
        ORDER BY i.name ASC
        """)
    Page<Item> searchItems(
        @Param("search") String search,
        @Param("category") Item.Category category,
        @Param("size") String size,
        Pageable pageable
    );
}
```

### DTO: `ItemSummaryResponse.java`

```java
// Returned in list view — no full photo list, just the primary thumbnail
public record ItemSummaryResponse(
    UUID id,
    String name,
    String category,
    String size,
    int ratePaise,           // frontend divides by 100 for display
    int depositPaise,
    int totalQuantity,
    int availableQuantity,   // computed: see AvailabilityService
    boolean isAvailable,     // availableQuantity > 0
    String thumbnailUrl      // first photo's thumbnail_url, null if no photos
) {}
```

### Service: `ItemService.java`

```java
@Service
@Transactional(readOnly = true)
public class ItemService {

    public Page<ItemSummaryResponse> listItems(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Item> items = itemRepository.findByIsActiveTrueOrderByNameAsc(pageable);
        return items.map(item -> {
            int available = availabilityService.getAvailableQuantity(item.getId(), null, null);
            String thumb = item.getPhotos().stream()
                .findFirst()
                .map(ItemPhoto::getThumbnailUrl)
                .orElse(null);
            return new ItemSummaryResponse(
                item.getId(), item.getName(), item.getCategory().name(),
                item.getSize(), item.getRatePaise(), item.getDepositPaise(),
                item.getQuantity(), available, available > 0, thumb
            );
        });
    }
}
```

> **Note on availableQuantity without date range:** When no date range is given (just browsing), show `quantity - count of GIVEN receipts for this item at any time`. This is a simplification — the availability check query (US-103) handles date-range-specific logic.

### Controller: `ItemController.java`

```java
@RestController
@RequestMapping("/api/items")
public class ItemController {

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ItemSummaryResponse>>> listItems(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) Item.Category category,
        @RequestParam(required = false) String itemSize
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            itemService.listItems(search, category, itemSize, page, size)
        ));
    }
}
```

### API Endpoint

```
GET /api/items?page=0&size=20
GET /api/items?page=0&size=20&search=sherwani&category=COSTUME&size=M

Response 200:
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "uuid",
        "name": "Blue Sherwani",
        "category": "COSTUME",
        "size": "M",
        "ratePaise": 20000,        // ₹200/day
        "depositPaise": 100000,    // ₹1,000 deposit
        "totalQuantity": 3,
        "availableQuantity": 2,
        "isAvailable": true,
        "thumbnailUrl": "https://pub-xxx.r2.dev/items/uuid/abc-thumb.webp"
      }
    ],
    "totalElements": 142,
    "totalPages": 8,
    "number": 0,
    "size": 20
  }
}
```

---

## Frontend Implementation

### Page: `src/pages/inventory/InventoryPage.tsx`

**Layout:** Responsive grid of item cards (3 columns on tablet landscape, 2 on portrait).

**Item Card component:**

```
┌─────────────────────────┐
│ [Photo thumbnail]       │
│ Blue Sherwani           │
│ Costume · Size M        │
│ ₹200/day               │
│ Deposit: ₹1,000         │
│ [2 available] ← green   │
│ [0 available] ← greyed  │
└─────────────────────────┘
```

**State management:**
- Use TanStack Query: `useQuery({ queryKey: ['items', page], queryFn: () => api.items.list(page) })`
- Pagination via Ant Design `Pagination` component
- Clicking a card opens `ItemDetailDrawer` (shows full details + all photos)

### API call

```ts
// src/api/items.ts
export const itemsApi = {
  list: (params: { page?: number; size?: number; search?: string; category?: string }) =>
    client.get<ApiResponse<PageResult<ItemSummary>>>('/items', { params }).then(r => r.data.data)
}
```

---

## Test Cases

| Scenario | Expected |
|----------|----------|
| All items are active, 25 items total | First page shows 20, second page shows 5 |
| Item has `is_active = false` | Not shown in list |
| Item has `quantity = 3`, all 3 currently rented (status=GIVEN) | Shows "0 available", card is greyed |
| Item has no photos | `thumbnailUrl` is null, placeholder image shown |
| Item has 2 photos | Shows first photo's thumbnail |
