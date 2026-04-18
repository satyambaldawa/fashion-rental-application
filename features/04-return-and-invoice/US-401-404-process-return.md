# US-401 / US-402 / US-403 / US-404: Process Return & Generate Invoice

**Epic:** Return Processing & Invoice Management
**Priority:** P0
**Depends On:** US-301 (receipt must exist), US-601 (late fee rules must be configured)
**Blocks:** US-701 (daily revenue reporting)

> These four stories form the return flow — the most complex part of the application. Implemented as a single atomic backend operation.

---

## User Stories

**US-401:** Process a return by selecting an active receipt and recording the return datetime.
**US-402:** Mark items as damaged and enter damage costs.
**US-403:** Auto-calculate late fees using time-range tiers.
**US-404:** Generate and display an invoice showing all charges and the final collect/refund amount.

---

## Acceptance Criteria

- [ ] Staff selects an active receipt and enters the return datetime (date + time, not just date)
- [ ] Staff can mark each line item as damaged and enter a damage percentage OR a flat ad hoc amount
- [ ] System calculates: late fees (if return_datetime > end_datetime), damage costs, deposit refund, and final net amount
- [ ] Minimum rental: 1 day — if returned before end_datetime, no reduction in rent charged (and no late fee)
- [ ] Invoice shows all line items clearly: rent, deposit collected, late fee, damage cost, deposit returned, final amount (COLLECT or REFUND)
- [ ] After return: receipt status → RETURNED, inventory units immediately available
- [ ] Invoice is permanently stored and displayed on screen

### Late Fee Algorithm

```
overdue_hours = return_datetime - end_datetime (in hours; negative = early return)

if overdue_hours <= 0: late_fee = 0   (returned on time or early)

else: find matching LateFeeRule where duration_from_hours <= overdue_hours
      (take the rule with the highest duration_from_hours that still qualifies)
      late_fee = daily_rate_paise × penalty_multiplier

      if no rule matches: late_fee = daily_rate_paise × 1.5  (default fallback)
```

### Damage Cost Calculation

```
if staff enters damage_percentage:
    damage_cost = item_rate_snapshot_paise × (damage_percentage / 100)

if staff enters ad_hoc_amount (in ₹):
    damage_cost = ad_hoc_amount × 100 (convert to paise)

total_damage_cost = sum across all damaged line items
```

### Invoice Calculation

```
deposit_collected   = receipt.total_deposit_paise
total_deductions    = total_late_fee_paise + total_damage_cost_paise
deposit_to_return   = max(0, deposit_collected - total_deductions)
balance_owed        = max(0, total_deductions - deposit_collected)

if balance_owed > 0:
    transaction_type = COLLECT, final_amount = balance_owed
else:
    transaction_type = REFUND, final_amount = deposit_to_return
```

---

## Worked Example

**Receipt:** Blue Sherwani ×2, Gold Necklace ×1, Apr 18 10:00 → Apr 20 10:00 (2 days)
- Rent paid: ₹1,350 (paise: 135000)
- Deposit collected: ₹2,500 (paise: 250000)

**Return datetime:** Apr 20 13:00 (3 hours late)
**Damage:** Blue Sherwani — 30% damage on one unit (rate: ₹200/day = 20000 paise)

**Late fee tier (0-3hr: 0.5x):**
- overdue_hours = 3.0 → matches tier "0-3 hours"
- Per item: daily_rate × 0.5 × qty = 20000 × 0.5 × 2 = 20000 paise (Sherwani) + 5000 × 0.5 × 1 = 2500 paise (Necklace)
- Total late fee = 22500 paise = ₹225

**Damage cost:**
- 30% of ₹200 = ₹60 = 6000 paise (for one Sherwani unit)
- Total damage = 6000 paise = ₹60

**Invoice calculation:**
```
Deposit collected:  ₹2,500
Late fee:           ₹225
Damage cost:        ₹60
Total deductions:   ₹285

Deposit to return:  ₹2,500 - ₹285 = ₹2,215
Final:              REFUND ₹2,215
```

---

## Backend Implementation

### Return Request DTO

```java
public record ProcessReturnRequest(
    @NotNull OffsetDateTime returnDatetime,
    @NotNull List<ReturnLineItem> lineItems,
    String paymentMethod,     // CASH | UPI | OTHER
    String damageNotes,
    String notes
) {}

public record ReturnLineItem(
    @NotNull UUID receiptLineItemId,
    boolean isDamaged,
    Double damagePercentage,  // null if not damaged or using ad hoc
    Integer adHocDamagePaise  // null if using percentage
) {}
```

### BillingService — Late Fee Calculation

```java
@Service
public class BillingService {

    public int calculateLateFee(
        OffsetDateTime endDatetime,
        OffsetDateTime returnDatetime,
        int dailyRatePaise,
        int quantity,
        List<LateFeeRule> rules
    ) {
        double overdueHours = dateTimeUtil.calculateOverdueHours(endDatetime, returnDatetime);
        if (overdueHours <= 0) return 0;

        // Find matching tier — highest duration_from_hours that is <= overdueHours
        LateFeeRule matched = rules.stream()
            .filter(r -> r.isActive() && r.getDurationFromHours() <= overdueHours)
            .max(Comparator.comparingInt(LateFeeRule::getDurationFromHours))
            .orElse(null);

        double multiplier = (matched != null) ? matched.getPenaltyMultiplier().doubleValue() : 1.5;
        return (int) Math.round(dailyRatePaise * multiplier * quantity);
    }

    public int calculateDamageCost(ReceiptLineItem lineItem, ReturnLineItem returnLine) {
        if (!returnLine.isDamaged()) return 0;

        if (returnLine.adHocDamagePaise() != null) {
            return returnLine.adHocDamagePaise();
        }
        if (returnLine.damagePercentage() != null) {
            double pct = returnLine.damagePercentage() / 100.0;
            return (int) Math.round(lineItem.getRateSnapshotPaise() * pct);
        }
        return 0;
    }
}
```

### ReturnService — Atomic Transaction

```java
@Service
@Transactional
public class ReturnService {

    public InvoiceResponse processReturn(UUID receiptId, ProcessReturnRequest request) {
        Receipt receipt = receiptRepository.findById(receiptId)
            .orElseThrow(() -> new ResourceNotFoundException("Receipt not found"));

        if (receipt.getStatus() == Receipt.Status.RETURNED) {
            throw new ConflictException("Receipt " + receipt.getReceiptNumber() + " is already returned");
        }

        List<LateFeeRule> rules = lateFeeRuleRepository.findByIsActiveTrueOrderBySortOrderAsc();

        int totalLateFee = 0;
        int totalDamage = 0;

        List<InvoiceLineItem> invoiceLines = new ArrayList<>();

        for (ReturnLineItem returnLine : request.lineItems()) {
            ReceiptLineItem rli = receiptLineItemRepository.findById(returnLine.receiptLineItemId())
                .orElseThrow();

            int lateFee = billingService.calculateLateFee(
                receipt.getEndDatetime(), request.returnDatetime(),
                rli.getRateSnapshotPaise(), rli.getQuantity(), rules
            );
            int damage = billingService.calculateDamageCost(rli, returnLine);

            totalLateFee += lateFee;
            totalDamage  += damage;

            InvoiceLineItem ili = new InvoiceLineItem();
            ili.setReceiptLineItem(rli);
            ili.setItem(rli.getItem());
            ili.setQuantityReturned(rli.getQuantity());
            ili.setIsDamaged(returnLine.isDamaged());
            ili.setDamagePercentage(returnLine.damagePercentage() != null
                ? BigDecimal.valueOf(returnLine.damagePercentage()) : null);
            ili.setDamageCostPaise(damage);
            ili.setLateFeePaise(lateFee);
            invoiceLines.add(ili);
        }

        int depositCollected = receipt.getTotalDepositPaise();
        int totalDeductions  = totalLateFee + totalDamage;
        int depositToReturn  = Math.max(0, depositCollected - totalDeductions);
        int balanceOwed      = Math.max(0, totalDeductions - depositCollected);

        Invoice.TransactionType txType;
        int finalAmount;
        if (balanceOwed > 0) {
            txType = Invoice.TransactionType.COLLECT;
            finalAmount = balanceOwed;
        } else {
            txType = Invoice.TransactionType.REFUND;
            finalAmount = depositToReturn;
        }

        // Build Invoice
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(invoiceNumberService.generate());
        invoice.setReceipt(receipt);
        invoice.setCustomer(receipt.getCustomer());
        invoice.setReturnDatetime(request.returnDatetime());
        invoice.setTotalRentPaise(receipt.getTotalRentPaise());
        invoice.setTotalDepositCollectedPaise(depositCollected);
        invoice.setTotalLateFeePaise(totalLateFee);
        invoice.setTotalDamageCostPaise(totalDamage);
        invoice.setDepositToReturnPaise(depositToReturn);
        invoice.setFinalAmountPaise(finalAmount);
        invoice.setTransactionType(txType);
        invoice.setPaymentMethod(Invoice.PaymentMethod.valueOf(
            request.paymentMethod() != null ? request.paymentMethod() : "CASH"
        ));
        invoice.setDamageNotes(request.damageNotes());
        invoice.setNotes(request.notes());
        invoice.setCreatedAt(OffsetDateTime.now());
        invoice.getLineItems().addAll(invoiceLines);
        invoiceLines.forEach(il -> il.setInvoice(invoice));

        invoiceRepository.save(invoice);

        // Mark receipt as returned
        receipt.setStatus(Receipt.Status.RETURNED);
        receipt.setUpdatedAt(OffsetDateTime.now());
        receiptRepository.save(receipt);

        // No explicit inventory update needed — availability query excludes RETURNED receipts automatically

        return toInvoiceResponse(invoice);
    }
}
```

### API Endpoint

```
POST /api/receipts/{id}/return
Body: ProcessReturnRequest
Response 201: InvoiceResponse
Response 409: already returned
Response 404: receipt not found
```

---

## Frontend Implementation

### Return Processing Page

**Step 1: Find Receipt**
- Search by receipt number or customer phone → select active receipt

**Step 2: Enter Return Details**
```
Receipt: R-20260418-003 — Ramesh Patel
Return Date/Time: [DateTimePicker — defaults to NOW]

Items:
┌──────────────────────────────────────────────────────────────┐
│ Blue Sherwani ×2         ☐ Damaged                          │
│   → [Damage %: ____] OR [Ad hoc amount ₹: ____]            │
├──────────────────────────────────────────────────────────────┤
│ Gold Necklace ×1         ☐ Damaged                          │
└──────────────────────────────────────────────────────────────┘

Damage Notes: [TextArea — optional]
Payment Method: [Cash / UPI / Other]
Notes: [TextArea — optional]

[Calculate & Preview Invoice]
```

**Step 3: Invoice Preview**
```
┌─────────────────────────────────────────────────┐
│           INVOICE - INV-20260420-002            │
│  Ramesh Patel · Apr 20, 2026 1:00 PM           │
│─────────────────────────────────────────────────│
│ Rental (2 days)              ₹1,350.00          │
│ Deposit Collected            ₹2,500.00          │
│─────────────────────────────────────────────────│
│ Late Fee (3 hrs)              ₹225.00           │
│ Damage (Blue Sherwani 30%)     ₹60.00           │
│─────────────────────────────────────────────────│
│ Total Deductions:             ₹285.00           │
│ Deposit Returned:           ₹2,215.00           │
│─────────────────────────────────────────────────│
│           REFUND: ₹2,215.00                     │
│           Payment Method: Cash                  │
└─────────────────────────────────────────────────┘
[Confirm & Complete Return]
```

---

## Test Cases

| Scenario | Expected |
|----------|----------|
| Returned on time, no damage | lateFee=0, damage=0, full deposit refunded |
| 3hrs late, tier=0-3hr: 0.5x | Late fee = rate × 0.5 per item |
| 25hrs late, tier=1-2day: 1.5x | Late fee = rate × 1.5 per item |
| Damage 30% on ₹200/day item | Damage = ₹60 = 6000 paise |
| Ad hoc damage ₹500 | Damage = 50000 paise regardless of rate |
| Late fee + damage > deposit | transaction_type=COLLECT, final=balance owed |
| Late fee + damage < deposit | transaction_type=REFUND, final=deposit remaining |
| Early return (5hrs early) | lateFee=0, full rental charged (no reduction) |
| Already returned receipt | 409 Conflict |
