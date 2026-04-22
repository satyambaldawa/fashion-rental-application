# US-301 / US-302 / US-303: Create Rental Receipt (Checkout Flow)

**Epic:** Checkout & Receipt Management
**Priority:** P0
**Depends On:** US-101 (inventory), US-103 (availability), US-201 (customers), US-601 (late fee config exists)
**Blocks:** US-401 (return processing)

> These three stories are implemented as a single checkout flow. US-301 is the full receipt creation, US-302 is the billing calculation, US-303 is multi-item support.

---

## User Stories

**US-301:** Create a rental receipt for a customer by selecting items, quantities, and rental datetimes.
**US-302:** Auto-calculate rental days and total rent from start/end datetimes.
**US-303:** Add multiple different items (with quantities) to a single receipt.

---

## Acceptance Criteria

- [x] Staff selects an existing customer (or creates one inline)
- [x] Staff selects a start datetime and end datetime
- [x] Staff adds one or more items with quantities
- [x] System validates availability for each item before adding to the order
- [x] System shows a live preview of totals (rental days, rent per item, total rent, total deposit, grand total)
- [x] On confirmation, Receipt is created atomically with a final availability recheck in a DB transaction
- [x] If availability changed between preview and confirm (race condition), API returns 409 and staff sees a clear error
- [x] Receipt status is set to `GIVEN` immediately
- [x] A human-readable receipt number is generated (e.g., `R-20260418-003`)
- [x] Receipt is displayed on screen after creation

### Billing Formula (US-302)

```
rentalDays = (end_datetime - start_datetime) in 24-hour increments  [minimum 1]
lineRent   = rate_snapshot_paise × rentalDays × quantity
lineDeposit = deposit_snapshot_paise × quantity
totalRent   = sum of all lineRents
totalDeposit = sum of all lineDeposits
grandTotal  = totalRent + totalDeposit
```

**Worked example:**
- Item A: "Blue Sherwani", ₹200/day, ₹1,000 deposit, qty = 2
- Item B: "Gold Necklace", ₹50/day, ₹500 deposit, qty = 1
- Rental: Apr 18 10:00 AM → Apr 21 10:00 AM = 3 days

```
Item A: ₹200 × 3 days × 2 = ₹1,200 rent | ₹1,000 × 2 = ₹2,000 deposit
Item B: ₹50  × 3 days × 1 = ₹150 rent   | ₹500  × 1 = ₹500 deposit

Total rent:    ₹1,350
Total deposit: ₹2,500
Grand total:   ₹3,850   ← amount customer pays now
```

---

## Backend Implementation

### Receipt Number Generation

Per-day sequential numbers in format `R-YYYYMMDD-NNN`:

```java
@Service
public class ReceiptNumberService {

    @Transactional
    public String generateReceiptNumber() {
        String today = LocalDate.now(ZoneId.of("Asia/Kolkata")).format(DateTimeFormatter.BASIC_ISO_DATE);
        String prefix = "R-" + today + "-";
        // Count receipts with today's prefix and increment
        long count = receiptRepository.countByReceiptNumberStartingWith(prefix);
        return prefix + String.format("%03d", count + 1);
    }
}
```

### Entity: `Receipt.java`

```java
@Entity
@Table(name = "receipts")
public class Receipt {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "receipt_number", nullable = false, unique = true)
    private String receiptNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "start_datetime", nullable = false)
    private OffsetDateTime startDatetime;

    @Column(name = "end_datetime", nullable = false)
    private OffsetDateTime endDatetime;

    @Column(name = "rental_days", nullable = false)
    private Integer rentalDays;

    @Column(name = "total_rent_paise")
    private Integer totalRentPaise = 0;

    @Column(name = "total_deposit_paise")
    private Integer totalDepositPaise = 0;

    @Column(name = "grand_total_paise")
    private Integer grandTotalPaise = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.GIVEN;

    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ReceiptLineItem> lineItems = new ArrayList<>();

    public enum Status { GIVEN, RETURNED }
}
```

### DTOs

```java
// Request to checkout preview and receipt creation (same shape)
public record CheckoutRequest(
    @NotNull UUID customerId,
    @NotNull OffsetDateTime startDatetime,   // ISO 8601 with timezone
    @NotNull OffsetDateTime endDatetime,
    @NotEmpty List<CheckoutLineItem> items,
    String notes
) {}

public record CheckoutLineItem(
    @NotNull UUID itemId,
    @NotNull @Min(1) Integer quantity
) {}

// Stateless preview response
public record CheckoutPreviewResponse(
    boolean allAvailable,
    List<PreviewLineItem> lineItems,
    int rentalDays,
    int totalRentPaise,
    int totalDepositPaise,
    int grandTotalPaise,
    List<String> unavailableItems    // names of items with insufficient availability
) {}
```

### Stateless Checkout Preview Endpoint

```java
// POST /api/checkout/preview — no DB write, pure computation
@PostMapping("/checkout/preview")
public ResponseEntity<ApiResponse<CheckoutPreviewResponse>> preview(
    @RequestBody @Valid CheckoutRequest request
) {
    return ResponseEntity.ok(ApiResponse.ok(checkoutService.preview(request)));
}
```

### CheckoutService.preview()

```java
@Transactional(readOnly = true)
public CheckoutPreviewResponse preview(CheckoutRequest request) {
    validateDateRange(request.startDatetime(), request.endDatetime());
    int rentalDays = dateTimeUtil.calculateRentalDays(request.startDatetime(), request.endDatetime());

    List<PreviewLineItem> lineItems = new ArrayList<>();
    List<String> unavailableItems = new ArrayList<>();

    for (CheckoutLineItem cli : request.items()) {
        Item item = itemRepository.findById(cli.itemId())
            .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + cli.itemId()));

        int available = availabilityService.getAvailableQuantity(
            cli.itemId(), request.startDatetime(), request.endDatetime()
        );

        if (available < cli.quantity()) {
            unavailableItems.add(item.getName() + " (need " + cli.quantity() + ", have " + available + ")");
        }

        int lineRent    = item.getRatePaise() * rentalDays * cli.quantity();
        int lineDeposit = item.getDepositPaise() * cli.quantity();

        lineItems.add(new PreviewLineItem(
            item.getId(), item.getName(), item.getRatePaise(), item.getDepositPaise(),
            cli.quantity(), rentalDays, lineRent, lineDeposit, available
        ));
    }

    int totalRent    = lineItems.stream().mapToInt(PreviewLineItem::lineRentPaise).sum();
    int totalDeposit = lineItems.stream().mapToInt(PreviewLineItem::lineDepositPaise).sum();

    return new CheckoutPreviewResponse(
        unavailableItems.isEmpty(), lineItems, rentalDays,
        totalRent, totalDeposit, totalRent + totalDeposit, unavailableItems
    );
}
```

### Receipt Creation — Atomic with Availability Recheck

```java
@Transactional
public ReceiptResponse createReceipt(CheckoutRequest request) {
    validateDateRange(request.startDatetime(), request.endDatetime());

    Customer customer = customerRepository.findById(request.customerId())
        .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

    int rentalDays = dateTimeUtil.calculateRentalDays(request.startDatetime(), request.endDatetime());

    // Authoritative availability recheck inside the transaction
    for (CheckoutLineItem cli : request.items()) {
        int available = availabilityService.getAvailableQuantity(
            cli.itemId(), request.startDatetime(), request.endDatetime()
        );
        if (available < cli.quantity()) {
            Item item = itemRepository.findById(cli.itemId()).orElseThrow();
            throw new ConflictException(
                item.getName() + " no longer has enough units available. Please review your order."
            );
        }
    }

    Receipt receipt = new Receipt();
    receipt.setReceiptNumber(receiptNumberService.generateReceiptNumber());
    receipt.setCustomer(customer);
    receipt.setStartDatetime(request.startDatetime());
    receipt.setEndDatetime(request.endDatetime());
    receipt.setRentalDays(rentalDays);
    receipt.setNotes(request.notes());
    receipt.setStatus(Receipt.Status.GIVEN);
    receipt.setCreatedAt(OffsetDateTime.now());
    receipt.setUpdatedAt(OffsetDateTime.now());

    int totalRent = 0, totalDeposit = 0;

    for (CheckoutLineItem cli : request.items()) {
        Item item = itemRepository.findById(cli.itemId()).orElseThrow();

        ReceiptLineItem line = new ReceiptLineItem();
        line.setReceipt(receipt);
        line.setItem(item);
        line.setQuantity(cli.quantity());
        line.setRateSnapshotPaise(item.getRatePaise());       // SNAPSHOT — not live rate
        line.setDepositSnapshotPaise(item.getDepositPaise()); // SNAPSHOT
        line.setLineRentPaise(item.getRatePaise() * rentalDays * cli.quantity());
        line.setLineDepositPaise(item.getDepositPaise() * cli.quantity());

        totalRent    += line.getLineRentPaise();
        totalDeposit += line.getLineDepositPaise();

        receipt.getLineItems().add(line);
    }

    receipt.setTotalRentPaise(totalRent);
    receipt.setTotalDepositPaise(totalDeposit);
    receipt.setGrandTotalPaise(totalRent + totalDeposit);

    return toReceiptResponse(receiptRepository.save(receipt));
}
```

### API Endpoints

```
POST /api/checkout/preview
Body: CheckoutRequest
Response 200: CheckoutPreviewResponse (no DB write)

POST /api/receipts
Body: CheckoutRequest
Response 201: ReceiptResponse
Response 409: item availability changed / insufficient units
Response 404: customer or item not found
Response 400: invalid date range or validation errors
```

---

## Frontend Implementation

### CheckoutPage — 3-Step Flow

```
Step 1: Select Customer
  [CustomerSearch component] → select or create new

Step 2: Select Rental Period
  Start datetime: [DateTimePicker]
  End datetime:   [DateTimePicker]
  → Shows calculated rental days live: "3 days"

Step 3: Add Items
  [ItemSearch with availability filter applied to chosen dates]
  For each item added:
    Item name | Rate ₹200/day | Qty [+/-] | Available: 2 | Line rent: ₹1,200

  ┌────────────────────────────────┐
  │ Rental Days:      3            │
  │ Total Rent:       ₹1,350       │
  │ Total Deposit:    ₹2,500       │
  │ Grand Total:      ₹3,850       │
  └────────────────────────────────┘
  [Preview & Confirm]
```

**Preview flow:**
1. Call `POST /api/checkout/preview` → show breakdown
2. Staff reviews totals
3. Click "Confirm & Create Receipt" → call `POST /api/receipts`
4. On success: navigate to Receipt display page
5. On 409: show which items are now unavailable, allow staff to adjust

---

## Test Cases

| Scenario | Expected |
|----------|----------|
| 2 items, 3 days, normal case | Receipt created, grand total = correct sum |
| End datetime ≤ start datetime | 400: invalid date range |
| Item has 2 units, requesting 3 | Preview shows unavailable; confirm blocked |
| Item becomes unavailable between preview and confirm | 409 on confirm with specific item name |
| Single item, 1 day rental | Grand total = rate + deposit |
| Customer not found | 404 |

**Worked example verification:**
- Item A: rate=20000 paise, deposit=100000 paise, qty=2, 3 days
  - lineRent = 20000 × 3 × 2 = 120000 paise = ₹1,200 ✓
  - lineDeposit = 100000 × 2 = 200000 paise = ₹2,000 ✓
- Item B: rate=5000 paise, deposit=50000 paise, qty=1, 3 days
  - lineRent = 5000 × 3 × 1 = 15000 paise = ₹150 ✓
  - lineDeposit = 50000 × 1 = 50000 paise = ₹500 ✓
- totalRent = 120000 + 15000 = 135000 paise = ₹1,350 ✓
- totalDeposit = 200000 + 50000 = 250000 paise = ₹2,500 ✓
- grandTotal = 385000 paise = ₹3,850 ✓
