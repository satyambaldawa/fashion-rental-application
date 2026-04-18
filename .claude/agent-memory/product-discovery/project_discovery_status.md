---
name: Discovery Document Status
description: Current state of fashion rental app product discovery - what's decided, what's pending
type: project
---

Discovery document v1.1 completed on 2026-04-18 with client clarifications.

Key decisions confirmed:
- Hybrid inventory tracking (pool count for identical items, separate entries for unique items)
- Damage cost = percentage-based calculation with ad hoc staff override
- Late fees = multiplier of daily rate, but time-range tiers (3hr/6hr/1day/2day/nday) under consideration
- PO and Receipt are separate entities (PO = preview/checkout, Receipt = confirmed rental)
- Receipt statuses: Given/Returned only for MVP
- Invoice uses TransactionType (COLLECT/REFUND) with positive amounts
- MVP output is on-screen display; printer/SMS/WhatsApp deferred
- Android tablet is target device (app or web TBD in tech discussion)

Deferred to later phases: advance booking, discounts, role-based access, multi-location, printer integration, SMS/WhatsApp

Still open (blocking): OQ-3 (inclusive vs exclusive day count)
Still open (non-blocking): OQ-5 (partial-day returns), OQ-6 (damaged beyond repair), OQ-13 (same-day re-rental), A-5 (late fee tier definitions)

**Why:** These decisions shape the data model, billing engine, and UI scope for MVP.
**How to apply:** Reference these decisions when writing PRDs, tech specs, or user stories. Flag any work that touches open items.
