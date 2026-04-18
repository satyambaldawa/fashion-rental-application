# Product Discovery Document
## Fashion Rental Management Application
### Physical Shop Digitization Initiative

---

**Document Version:** 1.2
**Prepared By:** Product Discovery & Requirements Engineering
**Date:** April 18, 2026
**Status:** Draft — Updated with Client Clarifications (v1.2)

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Goals & Success Metrics](#2-goals--success-metrics)
3. [Stakeholder & User Roles](#3-stakeholder--user-roles)
4. [Refined & Expanded User Stories](#4-refined--expanded-user-stories)
5. [Feature Capabilities](#5-feature-capabilities)
6. [Open Questions for Client](#6-open-questions-for-client)
7. [Assumptions](#7-assumptions)
8. [Out of Scope — MVP](#8-out-of-scope--mvp)
9. [Proposed MVP Scope](#9-proposed-mvp-scope)
10. [Data Model Clarifications & Gaps](#10-data-model-clarifications--gaps)

---

## 1. Executive Summary

### What Is This Product?

This application is a **point-of-sale and operations management platform** purpose-built for a physical fashion rental shop. It will be deployed in-store on a tablet or desktop and used by shop staff and the owner to manage the end-to-end lifecycle of costume and garment rentals — from inventory tracking and customer registration, through order creation and billing, to item return, damage assessment, deposit refund, and financial reporting.

### The Business

A brick-and-mortar fashion rental shop that rents out traditional costumes, accessories, and garments — including items for festivals (e.g., Garba/Navratri), cultural performances, weddings, and school or professional productions. The shop maintains a physical inventory of items across multiple categories: Costumes, Accessories, Pagdi (turbans/headwear), Dresses, and Ornaments.

### The Core Problem

Today the shop operates manually — likely through handwritten ledgers, physical receipts, or ad-hoc spreadsheets. This creates four critical operational pain points:

1. **Inventory visibility is poor.** Staff cannot instantly answer "do we have this item available on this date?" without checking physical records.
2. **Billing is error-prone.** Calculating rent, deposits, late fees, and damage costs by hand introduces mistakes and disputes.
3. **Revenue tracking is incomplete.** There is no consolidated view of daily takings, outstanding deposits, or pending returns.
4. **Customer records are fragmented.** Repeat customers cannot be looked up quickly; rental history is not accessible.

### The Solution

A structured digital platform that replaces manual processes with a fast, reliable, and auditable workflow covering:

- Inventory management with real-time availability per calendar date
- Customer registration and history
- Order (receipt) creation with automated billing calculation
- Return processing with late fee, damage, and deposit refund calculation
- Revenue and operational reporting for the owner

---

## 2. Goals & Success Metrics

### Business Goals

| # | Goal | Description |
|---|------|-------------|
| G1 | Reduce billing errors | Eliminate manual calculation mistakes for rent, deposits, and late fees |
| G2 | Improve inventory visibility | Enable staff to check real-time availability for any date in under 30 seconds |
| G3 | Speed up customer transactions | Reduce time to process a new rental from ~10 minutes to under 3 minutes |
| G4 | Centralize financial records | Provide the owner with a single source of truth for daily revenue, deposits held, and refunds issued |
| G5 | Reduce deposit disputes | Automated, transparent invoicing at return time with clear damage and late fee line items |
| G6 | Improve repeat customer experience | Look up any customer by phone number in under 5 seconds |

### Success Metrics (KPIs)

| Metric | Baseline (Pre-Launch) | Target (90 Days Post-Launch) |
|--------|----------------------|------------------------------|
| Average transaction time (new rental) | ~10 min (estimated) | < 3 min |
| Billing error rate | Unknown / untracked | 0% calculation errors |
| Inventory query time | Minutes (manual check) | < 30 seconds |
| Owner reporting time (daily summary) | Manual, 30–60 min | Automated, < 1 min |
| Customer record retrieval | Manual/slow | < 5 seconds by phone number |
| Overdue rental tracking | Manual / missed | 100% visibility via system |

---

## 3. Stakeholder & User Roles

### Roles Identified

| Role | Description | Primary Responsibilities |
|------|-------------|--------------------------|
| **Shop Staff (Counter Operator)** | Front-of-shop employee who handles day-to-day transactions | Creating receipts, processing returns, looking up inventory, registering customers |
| **Shop Owner / Manager** | Business owner or senior manager who oversees operations | Viewing reports, managing inventory catalog, setting pricing and deposit rules, reviewing financials |
| **Customer** | Person renting items (does not use the system directly) | Provides information; receives physical receipt/invoice |

> Note: In a small shop, the owner and staff may be the same person. The system must support a single-user deployment but should not preclude adding role-based access in the future.

### Needs by Role

**Shop Staff — Counter Operator**
- Quickly search and browse available items for a given date range
- Register new customers or retrieve existing customer records
- Generate a rental receipt with correct billing (rent + deposit)
- Process returns and generate invoices with late fees, damage, and refund amounts
- Check how many units of an item are currently available

**Shop Owner / Manager**
- View daily, weekly, and monthly revenue summaries
- Track all outstanding deposits (items not yet returned)
- See overdue rentals (past end date, not returned)
- Manage the item catalog: add new items, update pricing, upload photos
- Configure late fee rules and damage cost schedules
- Review full rental history for any customer or item

---

## 4. Refined & Expanded User Stories

Stories are organized by Epic. Priority is assigned as **P0** (must have for MVP), **P1** (important, near-term), or **P2** (valuable, later phase).

---

### Epic 1: Inventory Management

**US-101** *(refined from rough story #5)*
```
As a Shop Staff member,
I want to browse all items in the inventory with their details (name, size, category, rate, availability),
So that I can quickly identify suitable items for a customer.

Acceptance Criteria:
- Given I open the inventory view, I see a list of all items with name, category, size, rate, and current available quantity.
- When I scroll or paginate, all items load without delay.
- Items with zero available units are visually distinct (e.g., greyed out or marked "Unavailable").

Priority: P0
```

**US-102** *(refined from rough story #4)*
```
As a Shop Staff member,
I want to search for items in inventory by name, category, or size,
So that I can find specific items quickly without scrolling through the full catalog.

Acceptance Criteria:
- Given I enter a search term, matching items appear in real time or on submit.
- I can filter by category (Costume, Accessories, Pagdi, Dress, Ornaments) and/or size.
- If no results match, a clear "No items found" message is shown.

Priority: P0
```

**US-103** *(new)*
```
As a Shop Staff member,
I want to check how many units of a specific item are available on a specific date range,
So that I can confirm availability before creating a rental receipt for a customer.

Acceptance Criteria:
- Given I select an item and enter a start date and end date, the system shows the number of available units for that period.
- A unit is considered unavailable if it is linked to a receipt with status "Given" whose date range overlaps the requested range.
- If all units are booked, the system shows "0 available" and does not allow that item to be added to an order.

Priority: P0
```

**US-104** *(new)*
```
As a Shop Owner,
I want to add a new item to the inventory catalog,
So that newly purchased stock is immediately available for rental.

Acceptance Criteria:
- Given I fill in all required fields (name, category, size, rate, deposit amount) and submit, the item appears in inventory.
- I can optionally add a description, photographs, damage cost, and notes.
- The system assigns a unique Item ID automatically.
- I can specify the quantity of physical units for this item.

Priority: P0
```

**US-105** *(new)*
```
As a Shop Owner,
I want to edit an existing item's details (rate, deposit, description, photos),
So that the catalog stays accurate when pricing or item details change.

Acceptance Criteria:
- Given I select an item and edit a field, the change is saved and reflected immediately.
- Editing the rate does NOT retroactively change rates on existing active receipts.
- I can add or remove photographs.

Priority: P1
```

**US-106** *(new)*
```
As a Shop Owner,
I want to adjust the physical unit count of an item (increase or decrease),
So that I can account for new stock purchases or items retired due to irreparable damage.

Acceptance Criteria:
- Given I increase the unit count, additional units are immediately available for booking.
- Given I decrease the unit count, the system prevents reducing below the number of currently rented-out units.
- A reason/note field is available for the adjustment.

Priority: P1
```

---

### Epic 2: Customer Management

**US-201** *(new)*
```
As a Shop Staff member,
I want to register a new customer with their name, phone number, address, and type,
So that the customer has a profile that can be referenced on all their rentals.

Acceptance Criteria:
- Given I enter a phone number, the system checks for an existing customer record with that number.
- If no record exists, I can create a new profile.
- Customer type selection (Student / Professional / Misc) reveals the appropriate sub-field (School name / Organization name / none).
- Phone number is required and must be unique.

Priority: P0
```

**US-202** *(new)*
```
As a Shop Staff member,
I want to search for an existing customer by phone number or name,
So that I can retrieve their profile quickly when they arrive for a rental or return.

Acceptance Criteria:
- Given I enter a phone number or partial name, matching customer records appear.
- Selecting a customer shows their profile and full rental history.

Priority: P0
```

**US-203** *(new)*
```
As a Shop Staff member,
I want to view a customer's complete rental history (all receipts, invoices, outstanding balances),
So that I have full context when serving repeat customers and can identify any outstanding returns.

Acceptance Criteria:
- Given I open a customer profile, I see a chronological list of all their receipts.
- Each receipt shows its status (Given / Returned), dates, amounts, and links to the corresponding invoice if returned.
- Outstanding deposits are highlighted.

Priority: P1
```

**US-204** *(new)*
```
As a Shop Owner,
I want to edit a customer's contact details,
So that records stay accurate when a customer's address or phone number changes.

Acceptance Criteria:
- Phone number updates check for uniqueness.
- Changes are audited with a timestamp.

Priority: P1
```

---

### Epic 3: Order & Receipt Management

**US-301** *(refined from rough story #1)*
```
As a Shop Staff member,
I want to create a rental receipt for a customer by selecting items, quantities, and rental dates,
So that the system generates a documented rental agreement with accurate billing.

Acceptance Criteria:
- Given I select a customer (existing or new), choose items with quantities, and enter a start and end date, the system calculates: total rent (rate × days × quantity per item), total deposit (deposit amount × quantity per item), and grand total.
- The system only allows items with sufficient available units for the selected date range to be added.
- Upon saving, the receipt is assigned a unique Receipt ID and status is set to "Given".
- A printable receipt view is available.

Priority: P0
```

**US-302** *(new)*
```
As a Shop Staff member,
I want the system to automatically calculate the number of rental days and the total rent when I set start and end dates,
So that I never have to manually compute billing amounts.

Acceptance Criteria:
- The rental period is time-based (start_datetime to end_datetime), not calendar-date based.
- Days = difference between end_datetime and start_datetime measured in 24-hour increments (exclusive). Example: rented at 10 AM today, due back at 10 AM tomorrow = 1 day.
- Total rent per line item = rate × days × quantity.
- Grand total = sum of all line item rents + sum of all deposits.

Priority: P0
```

**US-303** *(new)*
```
As a Shop Staff member,
I want to add multiple different items (with quantities) to a single receipt,
So that a customer renting multiple items for one event gets a single consolidated rental agreement.

Acceptance Criteria:
- A receipt can contain one or more line items, each with an item reference, quantity, rate snapshot, and deposit snapshot.
- Adding an item updates the running totals in real time.
- I can remove a line item before saving the receipt.

Priority: P0
```

**US-304** *(new)*
```
As a Shop Staff member,
I want to view all active (not yet returned) receipts,
So that I know which items are currently out on rental and when they are due back.

Acceptance Criteria:
- The active receipts list shows receipt ID, customer name, items rented, end date, and days until due (or days overdue in red).
- I can sort and filter by due date.

Priority: P0
```

**US-305** *(new)*
```
As a Shop Staff member,
I want to view all overdue rentals (past end date, status still "Given"),
So that I can follow up with customers who have not returned items on time.

Acceptance Criteria:
- The system flags any receipt where end date < today and status = "Given".
- Overdue count and days overdue are clearly displayed.
- I can view customer contact information from this view.

Priority: P0
```

---

### Epic 4: Return Processing & Invoice Management

**US-401** *(refined from rough story #2)*
```
As a Shop Staff member,
I want to process an item return by selecting an active receipt and recording the return datetime,
So that the system generates an invoice with accurate late fees, damage costs, and deposit refund.

Acceptance Criteria:
- Given I select an active receipt and enter the return_datetime (timestamp, not just date), the system calculates: late fees (if return_datetime > end_datetime, using the configured late fee rules), damage costs (if any items are marked as damaged), deposit to be returned (total deposit − damage costs), and final amount (collect from customer if balance owed, or refund if deposit exceeds charges).
- return_datetime must be captured as a full timestamp (date + time) since late fee overdue duration is calculated in hours.
- Upon saving, the receipt status is updated to "Returned" and an Invoice is created.
- The returned item units become available in inventory again immediately.

Priority: P0
```

**US-402** *(updated per client clarification on OQ-1)*
```
As a Shop Staff member,
I want to mark specific items in a return as damaged and record the damage cost,
So that damage charges are captured in the invoice and deducted from the deposit.

Acceptance Criteria:
- During return processing, I can flag individual items as damaged.
- I can enter a damage percentage, and the system calculates: damage cost = damage percentage x item rate.
- Alternatively, I can enter an ad hoc flat damage amount directly (overriding the percentage calculation).
- The system sums all damage costs across items in the receipt.
- Damage cost is shown as a line item on the invoice.
- If damage cost > deposit collected, the invoice shows a COLLECT transaction for the difference.
- An optional damage_notes field is available for describing the damage.

Priority: P0
```

**US-403** *(updated per client clarification on OQ-2, OQ-5, and A-5)*
```
As a Shop Staff member,
I want the system to automatically calculate late fees when items are returned after the agreed end datetime,
So that customers are charged correctly and consistently without manual calculation.

Acceptance Criteria:
- The system calculates overdue duration in hours: overdue_hours = return_datetime − end_datetime.
- Late fee = penalty multiplier x daily rate, where the multiplier is determined by the applicable time-range tier (e.g., 0-3hrs, 3-6hrs, 6hrs-1day, 1-2days, 2+ days).
- Example: rented for 1 day, returned 3 hours late → 3 hours of late fee applies (matching the 0-3hr or 3-6hr tier as configured).
- Minimum rental duration is 1 day. An early return does not reduce the rental charge below 1 day (there is no negative late fee).
- The late fee is shown as a line item on the invoice.
- The system supports configurable time-range tiers with corresponding multipliers, settable by the owner.
- If no tier is configured, a default flat multiplier (e.g., 1.5x per overdue day) applies.

Priority: P0
```

**US-404** *(new)*
```
As a Shop Staff member,
I want to generate and print an invoice when items are returned,
So that the customer receives a clear, itemized breakdown of all charges and refunds.

Acceptance Criteria:
- The invoice clearly shows: original rent paid, deposit collected, late fee (if any), damage cost (if any), deposit to be returned, and the final net amount (collect or refund).
- The invoice is printable or can be displayed on screen for the customer.
- Invoice is permanently stored and linked to the original receipt.

Priority: P0
```

**US-405** *(new)*
```
As a Shop Owner,
I want to view all invoices for a given time period,
So that I can audit return transactions and review financials.

Acceptance Criteria:
- I can filter invoices by date range.
- Each invoice shows: invoice ID, receipt ID, customer, return date, total charges, and refund/collect amount.

Priority: P1
```

---

### Epic 5: Calendar & Availability Management

**US-501** *(new)*
```
As a Shop Staff member,
I want to view a calendar that shows how many units of an item are available on each day,
So that I can advise customers on available dates and plan rentals without double-booking.

Acceptance Criteria:
- For a selected item, the calendar shows available unit count per day (total units minus units on active rental on that day).
- Days with zero availability are visually marked as fully booked.
- The calendar supports at least a monthly view.

Priority: P1
```

**US-502** *(new)*
```
As a Shop Staff member,
I want the system to prevent me from booking an item for dates when no units are available,
So that over-booking is impossible and inventory integrity is maintained.

Acceptance Criteria:
- When creating a receipt, if an item has 0 available units for any day within the requested rental range, the system shows an error and does not allow the booking to be saved.

Priority: P0
```

---

### Epic 6: Pricing & Configuration

**US-601** *(updated per client clarification on A-5)*
```
As a Shop Owner,
I want to configure the late fee rules with time-range tiers and penalty multipliers,
So that late fee calculations are consistent and reflect the shop's policies.

Acceptance Criteria:
- I can define one or more time-range tiers (e.g., 0-3hrs, 3-6hrs, 6hrs-1day, 1-2days, 2+ days) with a penalty multiplier for each.
- The configured tiers are applied automatically to all future return calculations.
- Changes to the late fee rules do not retroactively affect already-generated invoices.
- A default rule (e.g., 1.5x per overdue day) is pre-configured and can be customized.

Priority: P0
```

**US-602** *(new)*
```
As a Shop Owner,
I want to define and update the deposit amount per item,
So that deposits accurately reflect the item's value and replacement cost.

Acceptance Criteria:
- Deposit amount is set per item in the catalog.
- Updating the deposit amount applies to new receipts only, not active rentals.

Priority: P0
```

---

### Epic 7: Reporting & Analytics

**US-701** *(new)*
```
As a Shop Owner,
I want to view a daily revenue summary showing total rent collected, total deposits collected, total refunds issued, and net cash flow,
So that I can reconcile cash at the end of each business day.

Acceptance Criteria:
- The daily summary shows a breakdown by transaction (receipt or invoice).
- It distinguishes between rent revenue and deposit collection (deposits are liabilities, not revenue).
- The report is filterable by date.

Priority: P0
```

**US-702** *(new)*
```
As a Shop Owner,
I want to see a report of all outstanding deposits (active rentals where deposit has been collected but items not yet returned),
So that I know my total deposit liability at any point in time.

Acceptance Criteria:
- The report lists all receipts with status "Given" and shows the deposit amount held for each.
- Total outstanding deposit is summed and displayed prominently.

Priority: P0
```

**US-703** *(new)*
```
As a Shop Owner,
I want to view a monthly revenue report with total rental income, late fee income, damage income, and refunds paid out,
So that I can monitor business performance over time.

Acceptance Criteria:
- Report is grouped by month and shows each revenue category separately.
- I can drill down into individual transactions for any month.

Priority: P1
```

**US-704** *(new)*
```
As a Shop Owner,
I want to see which items are rented most frequently and generate the most revenue,
So that I can make informed purchasing decisions for new inventory.

Acceptance Criteria:
- A report shows rental count and total revenue per item, sortable by either metric.
- Date range filter is available.

Priority: P2
```

---

## 5. Feature Capabilities

### 5.1 Inventory Management

| Feature | Description | Priority | Notes |
|---------|-------------|----------|-------|
| Item Catalog Browse | View all items with name, category, size, rate, quantity | P0 | Core daily operation |
| Item Search & Filter | Search by name; filter by category, size | P0 | Core daily operation |
| Real-Time Availability Check | Check available units for a date range | P0 | Prevents double-booking |
| Add New Item | Create catalog entry with all fields + photos | P0 | Owner only |
| Edit Item Details | Update pricing, description, photos | P1 | Owner only |
| Adjust Unit Count | Increase or decrease physical unit count | P1 | With audit note |
| Retire/Archive Item | Mark item as no longer available for rental | P2 | Soft delete |
| Bulk Import Items | Import multiple items via spreadsheet | P2 | Useful at launch |

### 5.2 Order & Receipt Management

| Feature | Description | Priority | Notes |
|---------|-------------|----------|-------|
| Create Rental Receipt | Select customer, items, dates; auto-calculate billing | P0 | Core transaction |
| Multi-Item Receipt | Add multiple items/quantities to one receipt | P0 | Core transaction |
| View Active Receipts | List all currently rented-out items | P0 | Daily operations |
| View Overdue Receipts | List all past-due rentals | P0 | Daily operations |
| Display Receipt on Screen | Show receipt on screen to customer (MVP); printer integration deferred | P0 | Confirmed by client for MVP |
| Edit Draft Receipt | Modify receipt before finalizing | P1 | Error correction |
| Cancel Receipt | Cancel a receipt before items are given out | P1 | With audit trail |

### 5.3 Invoice & Billing

| Feature | Description | Priority | Notes |
|---------|-------------|----------|-------|
| Process Return & Generate Invoice | Record return, compute fees, generate invoice | P0 | Core transaction |
| Automatic Late Fee Calculation | Apply configured late fee rules on overdue returns | P0 | Must be reliable |
| Damage Cost Entry | Flag items damaged; enter damage percentage (auto-calculated) or ad hoc flat amount | P0 | Percentage-based + manual override |
| Deposit Refund Calculation | Auto-calculate refund: deposit − damage − outstanding charges | P0 | Core transaction |
| Collect/Refund Determination | Invoice clearly states whether to collect or refund money | P0 | Customer-facing |
| Display Invoice on Screen | Show invoice on screen to customer (MVP); printer integration deferred | P0 | Customer-facing |
| View Invoice History | Browse all past invoices | P1 | Owner reporting |
| Late Fee Rule Configuration | Configure time-range tiers with penalty multipliers (e.g., 3hrs, 6hrs, 1day, 2days, n-days) | P0 | Owner setting; supports sub-day granularity |

### 5.4 Customer Management

| Feature | Description | Priority | Notes |
|---------|-------------|----------|-------|
| Register Customer | Create profile with name, phone, address, type | P0 | Pre-transaction |
| Search Customer | Find by phone number or name | P0 | Pre-transaction |
| View Customer Rental History | All receipts and invoices for a customer | P1 | Repeat customers |
| Edit Customer Details | Update contact information | P1 | Maintenance |
| Customer Type Classification | Student / Professional / Misc with sub-fields | P0 | Data model requirement |

### 5.5 Calendar & Availability

| Feature | Description | Priority | Notes |
|---------|-------------|----------|-------|
| Availability Prevention (Booking Guard) | Block booking when no units available | P0 | Data integrity |
| Item Availability Calendar | Visual calendar of available units per day | P1 | Planning tool |
| Availability Summary (Date-Based) | Show all items and their availability for a given date | P1 | Daily operations |

### 5.6 Reporting & Analytics

| Feature | Description | Priority | Notes |
|---------|-------------|----------|-------|
| Daily Revenue Summary | Rent collected, deposits collected, refunds issued | P0 | Daily reconciliation |
| Outstanding Deposit Report | All deposits held (items not yet returned) | P0 | Financial liability |
| Overdue Rentals Report | All past-due items with customer contact info | P0 | Operational urgency |
| Monthly Revenue Report | Revenue by category (rent, late fees, damage) | P1 | Business performance |
| Item Performance Report | Most-rented items, revenue per item | P2 | Strategic |
| Customer Lifetime Value | Total rental value per customer | P2 | Strategic |

---

## 6. Open Questions for Client

These items require answers before development begins. Each unresolved question carries risk of rework. Questions updated with client responses as of April 18, 2026.

| # | Question | Area | Status | Client Response |
|---|----------|------|--------|-----------------|
| OQ-1 | **How is damage cost determined?** | Damage / Invoice | PARTIALLY ANSWERED | Damage cost is derived from the percentage of damage and rate of item. The actual formula will be factored in later. Staff can also enter an ad hoc amount. |
| OQ-2 | **How are late fees calculated exactly?** | Late Fees / Invoice | ANSWERED | Multiplier of the daily rate (e.g., 1.5x per overdue day). |
| OQ-3 | **Are rental days calculated inclusively or exclusively?** | Billing | ANSWERED | Exclusive. Rental periods are time-based (start_datetime to end_datetime). Days = difference between end_datetime and start_datetime in 24-hour increments. Example: rented at 10 AM today, must be returned by 10 AM tomorrow = 1 day. |
| OQ-4 | **Can a single receipt span multiple customers?** | Receipt / Customer | ANSWERED | No. Always one customer per receipt. |
| OQ-5 | **How are partial-day returns handled?** | Late Fees | ANSWERED | Minimum rental is 1 day — early return within the rental period does not reduce the charge. If returned within the first 6 hours: still charged 1 full day (minimum applies). If returned late: late fees are charged per the time-range tier system. Overdue duration = return_datetime − end_datetime (in hours). Receipt stores start_datetime and end_datetime as full timestamps. |
| OQ-6 | **What happens when an item is damaged beyond repair?** | Damage / Inventory | ANSWERED | No special write-off workflow in MVP. The store operator manually enters the damage cost (staff enters whatever amount is appropriate). Owner manually adjusts the unit count if an item is retired. The existing damage cost entry on the invoice handles this case sufficiently for MVP. |
| OQ-7 | **Is there a concept of advance booking / reservations?** | Booking | ANSWERED | Yes, advance booking exists but is deferred to a later phase (low priority / Phase 3). |
| OQ-8 | **How are multiple physical units of the same item tracked?** | Inventory | ANSWERED | Hybrid model: identical items (exact replicas) are tracked together via pool count. Non-identical items are tracked individually as separate catalog entries. |
| OQ-9 | **What is the printing setup?** | Print / UX | ANSWERED (MVP) | MVP: display on screen to customer. Later phases: printer integration and SMS/WhatsApp delivery. |
| OQ-10 | **Is deposit always collected upfront and refunded at return?** | Deposit | ANSWERED | Yes. Deposit is always collected upfront and refunded at return. |
| OQ-11 | **Are there any discounts or special pricing?** | Pricing | ANSWERED (MVP) | Not in MVP. Deferred to future scope. |
| OQ-12 | **Who has access to what?** | Access Control | ANSWERED (MVP) | Not in MVP. Separate pages on the web app are sufficient for now. Role-based access to be added in a later phase. |
| OQ-13 | **How should the system handle same-day re-rental?** | Inventory | ANSWERED | If an item is returned at 10 AM, it is immediately available for re-rental. Availability is recalculated in real-time based on receipt status — when a receipt is marked Returned, those units are immediately available for new bookings. |
| OQ-14 | **Is there a need to support multiple shop locations?** | Architecture | ANSWERED (MVP) | Not in MVP. Deferred to future scope. |
| OQ-15 | **What devices will the application run on?** | Platform | PARTIALLY ANSWERED | Tablet (Android), either as an app or website. Final decision pending tech discussion. |

### Remaining Open Questions Summary

OQ-3, OQ-5, OQ-6, and OQ-13 have all been answered as of v1.2. The only remaining open item in Section 6 is **OQ-15** (final device/platform decision) and the **late fee tier multiplier values** (A-5), which do not block MVP development. All critical billing and return-processing questions are now resolved.

| # | Question | Risk Level | Blocker? |
|---|----------|-----------|----------|
| OQ-15 | Final device/platform decision (tablet app vs. web app) | Low | No — design responsive-first; final decision pending tech discussion |
| A-5 (pending) | Actual multiplier values per late fee time-range tier | Low | No — engine can be built with configurable tiers; values entered by owner before go-live |

---

## 7. Assumptions

These decisions were made during discovery and validated with the client on April 18, 2026. Validation status is noted for each.

| # | Assumption | Validation Status | Notes |
|---|-----------|-------------------|-------|
| A-1 | The application is a single-location, in-store tool — no e-commerce or customer-facing web portal in scope. | CONFIRMED | Future expansion to a user-facing web portal is a long-term aspiration ("50,000 feet view"). |
| A-2 | One customer per receipt (no group billing). | CONFIRMED | Always one customer per receipt. |
| A-3 | Hybrid unit tracking model: identical items (exact replicas) are managed as a pool count; non-identical items are tracked as separate catalog entries. | CONFIRMED (UPDATED) | Original assumption was pure pool-based. Updated to hybrid model per client clarification. |
| A-4 | Deposits are always collected upfront and always refunded at return (minus valid deductions). | CONFIRMED | No exceptions. |
| A-5 | Late fees are based on time-range tiers (3hrs, 6hrs, 1day, 2day, n-days). Overdue duration is calculated in hours (return_datetime − end_datetime). | CONFIRMED | Time-range tier system confirmed. The tiers (0-3hr, 3-6hr, 6hr-1day, 1-2day, 2+day) are the confirmed approach. The actual multiplier values per tier are still pending from the client but do not block MVP development — owner will configure them before go-live. |
| A-6 | The application requires no integration with external accounting software (e.g., Tally, QuickBooks) in MVP. | CONFIRMED | |
| A-7 | All transactions are in a single currency (INR). No multi-currency support required. | CONFIRMED | |
| A-8 | A single set of late fee rules applies to all items. Item-specific late fee rules are not required in MVP. | CONFIRMED | |
| A-9 | The system will be used on a tablet (Android) — either as a web app or native app. Final platform decision pending tech discussion. | CONFIRMED (MVP) | |
| A-10 | Data backup and storage are the responsibility of the deployment environment (local server or cloud host). The application itself does not need a built-in backup mechanism in MVP. | CONFIRMED | |
| A-11 | There is no requirement for SMS or WhatsApp notifications to customers in the MVP. | CONFIRMED (MVP) | SMS/WhatsApp integration planned for a later phase. |
| A-12 | Staff members share a single login. User-level audit trails per staff member are not required for MVP. | CONFIRMED | Role-based access is planned for a later phase. |
| A-13 | Rental periods are time-based (datetime), not calendar-date based. Days = difference between end_datetime and start_datetime in 24-hour increments (exclusive). | CONFIRMED (v1.2) | Receipt.start_datetime and Receipt.end_datetime must be full timestamps, not date-only fields. |
| A-14 | Minimum rental duration is 1 day (system constraint). Early return within the rental period does not reduce the charge — a customer cannot be billed less than 1 day regardless of when they return. | CONFIRMED (v1.2) | System enforces this at invoice calculation time. Return within first 6 hours still incurs 1 full day charge. |

### Key Assumption Change: Late Fee Model (A-5) — Now Confirmed

The original assumption that late fees are based on simple overdue calendar days has been updated and **confirmed**. Late fees use **time-range tiers**: 0-3 hours, 3-6 hours, 6hrs-1day, 1-2 days, 2+ days. This is a confirmed design requirement:

- The late fee calculation engine must support configurable time-range brackets with per-tier multipliers.
- The data model for `LateFeeRule` must accommodate duration ranges stored in hours with corresponding multipliers.
- Overdue duration is calculated as `return_datetime − end_datetime` in hours.
- The actual multiplier values per tier are still pending from the client and will be configured by the owner before go-live. This does not block MVP development.

The late fee rules UI and calculation logic must account for sub-day granularity.

---

## 8. Out of Scope — MVP

The following capabilities are acknowledged as potentially valuable but are explicitly deferred from the initial MVP to maintain focus and delivery speed. Items marked with an asterisk (*) were confirmed as out-of-scope by the client on April 18, 2026.

| Feature | Rationale for Deferral | Target Phase |
|---------|------------------------|--------------|
| Advance booking / reservations * | Client confirmed: exists as a concept but deferred | Phase 3 |
| Discounts and special pricing * | Client confirmed: not for MVP | Phase 2/3 |
| Role-based access control * | Client confirmed: separate pages sufficient for MVP; roles later | Phase 2 |
| Multiple shop locations * | Client confirmed: not for MVP | Phase 3 |
| Printer integration (thermal/A4) * | Client confirmed: screen display for MVP; printer later | Phase 2 |
| SMS / WhatsApp notifications * | Client confirmed: deferred; planned for later | Phase 2/3 |
| Customer-facing online booking portal | Significant additional scope; long-term aspiration per client | Phase 3+ |
| Integration with accounting software (Tally, QuickBooks) | Requires API integrations; not blocking for day-to-day operations | Phase 3 |
| Barcode / QR code scanning for items or customers | Hardware dependency; adds complexity; not blocking for core workflow | Phase 2/3 |
| E-commerce / online payments | Out of scope — this is an in-store management tool | Phase 3+ |
| Advanced analytics and dashboards (predictive demand, seasonal trends) | Requires historical data; more appropriate as a Phase 2 feature | Phase 2 |
| Customer loyalty / membership programs | Not discussed; requires separate product thinking | Phase 3+ |
| Automated overdue follow-up (call/message campaigns) | Operational; out of scope for software MVP | Phase 2/3 |
| Item condition photo capture at return | Nice-to-have for dispute resolution; increases complexity significantly | Phase 2 |

---

## 9. Proposed MVP Scope

### MVP Definition

The MVP is the smallest deployable system that allows the shop to fully replace their manual paper-based process with a digital workflow for the core rental lifecycle.

### MVP Must Include (P0)

| Domain | Capabilities Included |
|--------|----------------------|
| **Inventory** | Item catalog browse, search/filter, real-time availability check, add new item |
| **Customer** | Register customer, search customer (by phone/name), customer type classification |
| **Receipts** | Create rental receipt (single customer, multiple items), automatic billing calculation, availability guard, view active receipts, view overdue receipts |
| **Returns & Invoices** | Process return, damage cost entry, automatic late fee calculation, deposit refund calculation, generate and display invoice |
| **Configuration** | Late fee rule configuration, deposit amount per item |
| **Reporting** | Daily revenue summary, outstanding deposit report, overdue rentals report |

### MVP Explicitly Excludes

- Availability calendar view (replace with search-by-date availability check)
- Monthly/item-level reporting (replace with daily summary only)
- Print formatting (display on screen; basic browser print)
- Customer rental history view (item can be looked up in receipt list)

### Recommended Development Phases

**Phase 1 — MVP (Suggested: 6-8 weeks)**
Core transaction flow: Inventory (hybrid tracking model) + Customer + Receipt + PO (preview/checkout) + Return/Invoice + Basic Reporting + On-screen display for customer receipts/invoices

**Phase 2 — Operational Enhancements (Suggested: 4-6 weeks post-launch)**
Availability calendar, advanced reporting (monthly, item performance), customer rental history, invoice history, receipt editing and cancellation, unit count management, printer integration, role-based access control, discounts and special pricing

**Phase 3 — Growth Features (Suggested: Post Phase 2)**
Advance booking / reservations, SMS/WhatsApp reminders and receipt delivery, barcode scanning, multiple shop locations, accounting software integration, customer-facing online booking portal evaluation

### MVP Delivery Risk Flags

| Risk | Likelihood | Mitigation | Status |
|------|-----------|------------|--------|
| Late fee calculation rules are unclear (OQ-2) | ~~High~~ **Resolved** | Late fee = multiplier of daily rate per overdue day (e.g., 1.5x). However, time-range tiers (A-5) still pending — design engine to be configurable. | REDUCED |
| Damage cost model undefined (OQ-1) | ~~High~~ **Low** | Damage cost = percentage of damage x item rate. Actual formula TBD. Staff can also enter ad hoc. Design with percentage-based default + manual override. | REDUCED |
| Day count calculation ambiguity (OQ-3) | ~~Medium~~ **Resolved** | Exclusive, datetime-based. Days = (end_datetime − start_datetime) in 24-hour increments. Receipts store full timestamps. | RESOLVED |
| Device/platform unclear (OQ-15) | Low | Confirmed: Android tablet, app or website. Final decision pending tech discussion. Design responsive-first for tablet. | PARTIALLY RESOLVED |
| Late fee time-range tiers (A-5) | ~~Medium~~ **Reduced** | Tiers confirmed (0-3hr, 3-6hr, 6hr-1day, 1-2day, 2+day). Design late fee engine with configurable time-range brackets. Actual multiplier values pending — owner configures before go-live. | REDUCED |

---

## 10. Data Model Clarifications & Gaps

The following observations are based on the data model shared in stakeholder sessions. These are not criticisms — they are clarifications and questions that must be resolved before schema design begins.

### 10.1 Item & Unit Tracking

**Client Decision (OQ-8 — ANSWERED):** Hybrid model confirmed.

- **Identical items (exact replicas):** Tracked together using a pool count (quantity field on the inventory record). Example: 5 identical red dupattas are one catalog entry with quantity = 5.
- **Non-identical items:** Each tracked as a separate catalog entry with its own details, pricing, and quantity (typically 1). Example: two different lehenga designs are two separate items even if they are in the same category and size.

**Design Implication:** No `ItemUnit` entity is needed for MVP. The existing `Item` + `Inventory (Quantity)` model is sufficient. Each catalog entry represents either a unique item (qty = 1) or a pool of identical replicas (qty > 1). Damage tracking at the individual-unit level within a pool is not required for MVP.

---

### 10.2 Purchased Order (PO) vs. Receipt

**Client Decision — ANSWERED:** PO and Receipt are distinct entities with different purposes.

- **PO (Purchased Order):** The checkout/preview page where staff can review selected items, costs, days, and totals before confirming. This is the "cart" or "order preview" stage.
- **Receipt:** The confirmed rental document that is printed/displayed and handed to the customer. Created when the PO is finalized.

**Design Implication:** Keep as separate entities. The workflow is: Staff builds a PO (selects items, dates, quantities) -> reviews totals on the PO screen -> confirms -> system generates a Receipt with status "Given". The PO serves as the draft/preview; the Receipt is the committed rental agreement.

---

### 10.3 Damage Cost on Item vs. Invoice

**Client Decision (OQ-1 — PARTIALLY ANSWERED):** Damage cost uses a percentage-based model with ad hoc override.

- **Calculation method:** Damage cost = (percentage of damage) x (rate of the item). The exact formula for determining the "percentage of damage" will be defined later.
- **Ad hoc override:** Staff can also enter a damage cost manually, bypassing the formula.
- **Item-level field:** The `Damage cost` field on the Item entity can serve as a reference or maximum value, but the actual charge at return time is calculated or entered by staff.

**Design Implication:** At return time, the system should:
1. Allow staff to enter a damage percentage, which the system multiplies by the item rate to compute damage cost.
2. Alternatively, allow staff to enter an ad hoc flat damage amount directly.
3. The computed or entered amount flows into the Invoice as a line item.

The exact percentage tiers or assessment criteria are pending. For MVP, support both entry modes (percentage-based and flat ad hoc).

---

### 10.4 Late Fees Entity

**Partially Resolved (OQ-2 + A-5):** The base late fee model is confirmed as a multiplier of the daily rate (e.g., 1.5x per overdue day). However, the client has indicated that late fees may use **time-range tiers** rather than simple calendar-day counts.

**Client-indicated time ranges:** 3 hours, 6 hours, 1 day, 2 days, n-days.

This suggests a tiered `LateFeeRule` structure:

| Duration Range | Multiplier (Example) |
|---------------|---------------------|
| 0 - 3 hours overdue | TBD |
| 3 - 6 hours overdue | TBD |
| 6 hours - 1 day overdue | TBD |
| 1 - 2 days overdue | TBD |
| 2+ days overdue | TBD |

**Design Implication:** The `LateFeeRule` table should support:
- `duration_from` and `duration_to` fields (stored in hours for sub-day granularity)
- `penalty_multiplier` per tier
- The system calculates overdue duration in hours, matches to the appropriate tier, and applies the multiplier against the daily rate.

Final tier definitions and multiplier values are pending from the client. For MVP, build the engine to be configurable via these tiers.

---

### 10.5 Receipt & Invoice Datetime Fields (Updated v1.2)

**Client Decision (OQ-3, OQ-5 — ANSWERED):** Rental periods are time-based, not calendar-date based.

**Design Implication:** The following fields must be full timestamps (datetime), not date-only fields:

| Entity | Field | Type | Reason |
|--------|-------|------|--------|
| Receipt | `start_datetime` | TIMESTAMP | Rental period start — time of day matters for day count calculation |
| Receipt | `end_datetime` | TIMESTAMP | Rental period end — time of day matters; due back at same time of day |
| Invoice / Return | `return_datetime` | TIMESTAMP | Required for late fee calculation in hours (overdue_hours = return_datetime − end_datetime) |

**Day count calculation:** `days = (end_datetime − start_datetime)` in 24-hour increments (exclusive). A rental from 10:00 AM Day 1 to 10:00 AM Day 2 = 1 day.

**Late fee calculation:** `overdue_hours = return_datetime − end_datetime` (in hours). The result is matched to the applicable `LateFeeRule` tier.

**Minimum rental enforcement:** If `return_datetime < end_datetime` (early return), the system still charges for the full agreed rental period (minimum 1 day). No reduction applied.

**Same-day re-rental:** When a receipt is marked Returned (return_datetime recorded), inventory availability is recalculated immediately in real-time. No cooldown period. Units are available for new bookings the moment the return is saved.

---

### 10.6 Receipt Status: Missing Intermediate States

**Client Decision — ANSWERED:** Additional statuses (Draft, Partially Returned, Cancelled) are **not needed for MVP**.

MVP receipt statuses remain: **Given** and **Returned** only.

Note: The PO entity (Section 10.2) effectively serves the "Draft" role — a PO is the uncommitted preview, and once confirmed it becomes a Receipt with status "Given". This removes the need for a Draft status on the Receipt itself.

---

### 10.7 Invoice Final Amount Sign Convention

**Client Decision — ANSWERED:** Use a separate `TransactionType` field (`COLLECT` / `REFUND`) with a positive amount.

- `TransactionType = COLLECT` + `Amount = 200` means: collect 200 from the customer.
- `TransactionType = REFUND` + `Amount = 500` means: refund 500 to the customer.

This is clearer for staff reading the invoice and avoids sign-convention confusion.

---

### 10.8 Missing Fields to Consider

| Entity | Missing Field | Client Decision | Include in MVP? |
|--------|---------------|-----------------|-----------------|
| Receipt | `created_by` (staff member) | "No need" | No |
| Receipt | `notes` free-text field | "Ok" | Yes |
| Invoice | `damage_notes` | "Ok, not mandatory" | Yes (optional field) |
| Invoice | `payment_method` (cash / UPI / other) | "Ok" | Yes |
| Customer | `email` (optional) | "Not needed" | No |
| Item | `minimum_rental_days` | "Minimum 1 day for all, so it is a system constraint" | No (hardcode as system rule: min 1 day) |
| Item | `is_active` boolean | "Ok" | Yes |

---

---

## Changelog

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | April 17, 2026 | Initial discovery document |
| 1.1 | April 18, 2026 | Updated with client clarifications: 11 of 15 open questions answered/partially answered; 12 of 12 assumptions validated; data model decisions on PO vs Receipt, damage model, unit tracking, receipt status, invoice transaction type, and field additions confirmed |
| 1.2 | April 18, 2026 | Answered OQ-3 (day counting: exclusive, datetime-based), OQ-5 (partial-day returns: minimum 1 day, late fees in hours per tier), OQ-6 (damaged-beyond-repair: manual damage cost entry + manual unit count adjustment, no special write-off workflow in MVP), OQ-13 (same-day re-rental: immediate availability on return). Confirmed A-5 late fee tiers. Added A-13 (datetime-based rental periods) and A-14 (minimum rental 1 day). Added Section 10.5 (Receipt & Invoice Datetime Fields). Updated US-302, US-401, US-403. Updated Section 9 risk register. All four previously open questions are now resolved. |

---

*End of Document*

---

**Status as of v1.2:** All previously open billing and return-processing questions are now resolved. OQ-3, OQ-5, OQ-6, and OQ-13 are answered. The late fee tier system (A-5) is confirmed; only the specific multiplier values per tier remain outstanding, which the owner will configure before go-live — this does not block development. The only remaining open question is OQ-15 (final device/platform decision), which is low-risk. The team can proceed with MVP development with full confidence on the core transaction flow, billing calculations, and data model field types.
