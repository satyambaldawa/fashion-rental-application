# US-701 / US-702 / US-703: Reporting — Daily Revenue, Outstanding Deposits, Overdue Rentals

**Epic:** Reporting & Analytics
**Priority:** P0
**Depends On:** US-301, US-401 (data must exist to report on)
**Blocks:** Nothing

---

## User Stories

**US-701:** View daily revenue summary — total rent collected, deposits collected, refunds issued, net cash flow.
**US-702:** See all outstanding deposits (active rentals where deposit collected, items not returned).
**US-703:** View overdue rentals report with customer contact info.

---

## Acceptance Criteria

**US-701 — Daily Revenue:**
- [ ] Shows all receipts created on the selected date (deposits collected + rent paid)
- [ ] Shows all invoices settled on the selected date (deposits refunded/collected, late fees, damage income)
- [ ] Clearly separates rent revenue from deposit collection (deposits are liabilities)
- [ ] Date filter defaults to today
- [ ] Amounts in ₹, not paise

**US-702 — Outstanding Deposits:**
- [ ] Lists all active receipts (status = GIVEN) with deposit held
- [ ] Shows customer name, phone, items, deposit amount, days since rented
- [ ] Total outstanding deposit prominently shown

**US-703 — Overdue Rentals:**
- [ ] Lists all receipts where end_datetime < now and status = GIVEN
- [ ] Shows customer phone for easy follow-up
- [ ] Overdue duration shown in hours/days

---

## Backend Implementation

### ReportingService

```java
@Service
@Transactional(readOnly = true)
public class ReportingService {

    // US-701: Daily Revenue Summary
    public DailyRevenueResponse getDailyRevenue(LocalDate date, ZoneId zone) {
        OffsetDateTime dayStart = date.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime dayEnd   = date.plusDays(1).atStartOfDay(zone).toOffsetDateTime();

        // Receipts created today (money collected from customers)
        List<Receipt> receiptsCreated = receiptRepository.findByCreatedAtBetween(dayStart, dayEnd);
        int rentCollected     = receiptsCreated.stream().mapToInt(Receipt::getTotalRentPaise).sum();
        int depositsCollected = receiptsCreated.stream().mapToInt(Receipt::getTotalDepositPaise).sum();

        // Invoices settled today (returns processed)
        List<Invoice> invoicesSettled = invoiceRepository.findByCreatedAtBetween(dayStart, dayEnd);
        int depositsRefunded = invoicesSettled.stream()
            .filter(i -> i.getTransactionType() == Invoice.TransactionType.REFUND)
            .mapToInt(Invoice::getFinalAmountPaise).sum();
        int collectedFromCustomers = invoicesSettled.stream()
            .filter(i -> i.getTransactionType() == Invoice.TransactionType.COLLECT)
            .mapToInt(Invoice::getFinalAmountPaise).sum();
        int lateFeeIncome   = invoicesSettled.stream().mapToInt(Invoice::getTotalLateFeePaise).sum();
        int damageIncome    = invoicesSettled.stream().mapToInt(Invoice::getTotalDamageCostPaise).sum();

        // Net cash flow: money in (rent + deposits collected + collect-from-customers) minus money out (refunds)
        int netCashIn  = rentCollected + depositsCollected + collectedFromCustomers;
        int netCashOut = depositsRefunded;
        int netFlow    = netCashIn - netCashOut;

        return new DailyRevenueResponse(
            date, rentCollected, depositsCollected, depositsRefunded,
            collectedFromCustomers, lateFeeIncome, damageIncome, netFlow,
            receiptsCreated.size(), invoicesSettled.size()
        );
    }

    // US-702: Outstanding Deposits
    public OutstandingDepositsResponse getOutstandingDeposits() {
        List<Receipt> activeReceipts = receiptRepository.findByStatusOrderByEndDatetimeAsc(Receipt.Status.GIVEN);
        int totalOutstanding = activeReceipts.stream().mapToInt(Receipt::getTotalDepositPaise).sum();

        List<OutstandingDepositItem> items = activeReceipts.stream().map(r -> {
            long daysSinceRented = ChronoUnit.DAYS.between(r.getStartDatetime(), OffsetDateTime.now());
            return new OutstandingDepositItem(
                r.getId(), r.getReceiptNumber(),
                r.getCustomer().getName(), r.getCustomer().getPhone(),
                r.getLineItems().stream().map(li -> li.getItem().getName() + " ×" + li.getQuantity()).toList(),
                r.getTotalDepositPaise(), r.getEndDatetime(), (int) daysSinceRented
            );
        }).toList();

        return new OutstandingDepositsResponse(totalOutstanding, items);
    }

    // US-703: Overdue Rentals (same as US-305 list but formatted as a report)
    public OverdueRentalsResponse getOverdueRentals() {
        OffsetDateTime now = OffsetDateTime.now();
        List<Receipt> overdue = receiptRepository.findOverdue(now);

        List<OverdueRentalItem> items = overdue.stream().map(r -> {
            double overdueHours = dateTimeUtil.calculateOverdueHours(r.getEndDatetime(), now);
            return new OverdueRentalItem(
                r.getId(), r.getReceiptNumber(),
                r.getCustomer().getName(), r.getCustomer().getPhone(),
                r.getLineItems().stream().map(li -> li.getItem().getName() + " ×" + li.getQuantity()).toList(),
                r.getEndDatetime(), overdueHours
            );
        }).toList();

        return new OverdueRentalsResponse(items.size(), items);
    }
}
```

### API Endpoints

```
GET /api/reports/daily-revenue?date=2026-04-18
Response 200:
{
  "success": true,
  "data": {
    "date": "2026-04-18",
    "rentCollectedPaise": 135000,           // ₹1,350 from new rentals
    "depositsCollectedPaise": 250000,        // ₹2,500 deposit liability taken on
    "depositsRefundedPaise": 221500,         // ₹2,215 refunded on returns
    "collectedFromCustomersPaise": 0,        // ₹0 extra collected from customers
    "lateFeeIncomePaise": 22500,            // ₹225 late fee revenue
    "damageIncomePaise": 6000,              // ₹60 damage revenue
    "netFlowPaise": 163500,                  // ₹1,635 net
    "newReceiptsCount": 2,
    "returnsProcessedCount": 1
  }
}

GET /api/reports/outstanding-deposits
Response 200:
{
  "success": true,
  "data": {
    "totalOutstandingPaise": 400000,        // ₹4,000 total deposit liability
    "items": [
      {
        "receiptId": "uuid",
        "receiptNumber": "R-20260418-003",
        "customerName": "Ramesh Patel",
        "customerPhone": "9876543210",
        "itemNames": ["Blue Sherwani ×2"],
        "depositPaise": 200000,
        "endDatetime": "2026-04-20T10:00:00+05:30",
        "daysSinceRented": 2
      }
    ]
  }
}

GET /api/reports/overdue-rentals
Response 200:
{
  "success": true,
  "data": {
    "overdueCount": 1,
    "items": [
      {
        "receiptId": "uuid",
        "receiptNumber": "R-20260415-001",
        "customerName": "Priya Shah",
        "customerPhone": "9988776655",
        "itemNames": ["Red Lehenga ×1"],
        "endDatetime": "2026-04-17T10:00:00+05:30",
        "overdueHours": 51.5
      }
    ]
  }
}
```

---

## Frontend Implementation

### Reports Page — Tab Layout

```
[Daily Revenue]  [Outstanding Deposits]  [Overdue Rentals]

── Daily Revenue ─────────────────────────────────────
Date: [DatePicker — default today]

📊 April 18, 2026
┌────────────────────────────────────────────────────┐
│ New Rentals (2 receipts)                           │
│   Rent collected:        ₹1,350.00                 │
│   Deposits taken on:     ₹2,500.00                 │
│                                                    │
│ Returns Processed (1 invoice)                      │
│   Deposits refunded:     ₹2,215.00                 │
│   Late fee income:         ₹225.00                 │
│   Damage income:            ₹60.00                 │
│                                                    │
│ Net Cash Flow:            ₹1,635.00 ← green        │
└────────────────────────────────────────────────────┘

── Outstanding Deposits ──────────────────────────────
Total Outstanding: ₹4,000.00 (3 rentals)

R-20260418-003  Ramesh Patel  📱 9876543210
Blue Sherwani ×2 | Due: Apr 20 10:00 | Deposit: ₹2,000
[Process Return]

── Overdue Rentals ───────────────────────────────────
🔴 1 overdue rental

R-20260415-001  Priya Shah  📱 9988776655
Red Lehenga ×1 | Was due: Apr 17 10:00 | Overdue: 2 days 3 hrs
[Process Return]
```

---

## Test Cases

| Scenario | Expected |
|----------|----------|
| No receipts on selected date | All amounts = 0, counts = 0 |
| 3 receipts created today | rentCollected = sum of all 3, depositsCollected = sum of all 3 |
| 1 return with COLLECT outcome | collectedFromCustomers = that amount |
| 1 return with REFUND outcome | depositsRefunded = refund amount |
| No GIVEN receipts | Outstanding deposits total = 0, empty list |
| All returned on time | Overdue count = 0 |
