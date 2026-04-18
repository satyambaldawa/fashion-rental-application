# US-203: View Customer Rental History

**Epic:** Customer Management
**Priority:** P1
**Depends On:** US-202, US-301, US-401
**Blocks:** Nothing

---

## User Story

> As a Shop Staff member, I want to view a customer's complete rental history (all receipts, invoices, outstanding balances), so that I have full context when serving repeat customers.

---

## Acceptance Criteria

- [ ] Shows all receipts for the customer in reverse chronological order
- [ ] Each receipt shows: receipt number, items rented (names + qty), start/end datetime, status (Given/Returned), total rent, deposit amount
- [ ] For returned receipts, links to the corresponding invoice
- [ ] Outstanding deposits (status = GIVEN) are highlighted
- [ ] Total outstanding deposit amount shown at the top

---

## Backend Implementation

### API Endpoint

```
GET /api/customers/{id}

Response 200:
{
  "success": true,
  "data": {
    "id": "uuid",
    "name": "Ramesh Patel",
    "phone": "9876543210",
    "customerType": "PROFESSIONAL",
    "organizationName": "Patel & Sons Ltd",
    "address": "42, Gandhi Nagar, Ahmedabad",
    "outstandingDepositPaise": 150000,
    "receipts": [
      {
        "id": "uuid",
        "receiptNumber": "R-20260418-003",
        "status": "GIVEN",
        "startDatetime": "2026-04-18T10:00:00+05:30",
        "endDatetime": "2026-04-20T10:00:00+05:30",
        "totalRentPaise": 40000,
        "totalDepositPaise": 150000,
        "grandTotalPaise": 190000,
        "items": [
          { "itemName": "Blue Sherwani", "quantity": 2 }
        ],
        "invoice": null   // null if not yet returned
      },
      {
        "id": "uuid",
        "receiptNumber": "R-20260410-001",
        "status": "RETURNED",
        "startDatetime": "2026-04-10T10:00:00+05:30",
        "endDatetime": "2026-04-12T10:00:00+05:30",
        "totalRentPaise": 20000,
        "totalDepositPaise": 100000,
        "grandTotalPaise": 120000,
        "items": [{ "itemName": "Red Lehenga", "quantity": 1 }],
        "invoice": {
          "id": "uuid",
          "invoiceNumber": "INV-20260412-001",
          "finalAmountPaise": 5000,
          "transactionType": "COLLECT"
        }
      }
    ]
  }
}
```

### Service

```java
@Transactional(readOnly = true)
public CustomerDetailResponse getCustomer(UUID id) {
    Customer customer = customerRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));

    List<Receipt> receipts = receiptRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId());

    int outstandingDeposit = receipts.stream()
        .filter(r -> r.getStatus() == Receipt.Status.GIVEN)
        .mapToInt(Receipt::getTotalDepositPaise)
        .sum();

    return toDetailResponse(customer, receipts, outstandingDeposit);
}
```

---

## Frontend Implementation

### Customer Detail Page

```
Ramesh Patel
📱 9876543210 · Professional · Patel & Sons Ltd

┌─────────────────────────────────────────┐
│ ⚠️  Outstanding Deposit: ₹1,500.00      │
│     (1 item currently out)              │
└─────────────────────────────────────────┘

Rental History
──────────────
R-20260418-003          [GIVEN] ← orange badge
Apr 18 10:00 → Apr 20 10:00 (2 days)
Blue Sherwani ×2
Rent: ₹400 | Deposit: ₹1,500 | Total: ₹1,900
──────────────
R-20260410-001          [RETURNED] ← green badge
Apr 10 → Apr 12 (2 days)
Red Lehenga ×1
Rent: ₹200 | Deposit: ₹1,000 | Total: ₹1,200
Invoice: INV-20260412-001 · Collected ₹50  [View Invoice]
```

---

## Test Cases

| Scenario | Expected |
|----------|----------|
| Customer with no receipts | Empty history, outstanding = ₹0 |
| Customer with 2 GIVEN, 1 RETURNED | Outstanding deposit = sum of 2 GIVEN deposits |
| RETURNED receipt with invoice | Invoice summary and link shown |
