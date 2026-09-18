# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

**Shop Owner (OWNER role).** Runs the business end-to-end: manages the item catalog and pricing, configures late fee rules, creates/manages staff accounts, reviews reports, and also works the counter day-to-day.

**Hired Staff (EXECUTIVE role).** Real staff members, not a placeholder — confirmed during init that the shop now has hired staff alongside the owner, not just a solo operator. They handle counter operations (checkout, returns, customer lookup) but are blocked from inventory writes, reports, and settings/config.

**Customer.** Does not use the system directly. Provides information at the counter; receives a physical receipt/invoice.

Both OWNER and EXECUTIVE typically operate in-store, at the counter, often with a customer physically waiting — frequently on a shared Android tablet running this app as a PWA. Transactions need to be fast under that time pressure.

## Product Purpose

Replaces a physical rental shop's manual ledger/spreadsheet process with a structured digital platform covering the full rental lifecycle: real-time inventory availability by date, customer registration and history, receipt creation with automated billing, return processing with late fee/damage/deposit calculation, and owner-facing reporting.

Success means: billing errors eliminated, availability checks under 30 seconds, new-rental transactions under 3 minutes, customer lookup by phone under 5 seconds, and the owner getting a same-day view of revenue, outstanding deposits, and overdue rentals without manual reconciliation.

## Positioning

Confirmed during init: this is being built for the one shop first, but a future where it's offered to other rental businesses hasn't been ruled out — so product boundaries (roles, terminology, the rental lifecycle model) should stay clean enough to generalize later, even though nothing is being built speculatively for that audience yet.

Its mechanism versus a generic POS or spreadsheet: the domain model is rental-specific from the ground up — date-range availability per physical unit (not just per SKU), deposits as a first-class concept distinct from rent, late fee tiers, and package items (a bundle billed as one line while each component's inventory is independently reserved).

## Operating Context

- In-store, at a physical counter, on a shared Android tablet (PWA) or desktop — not a back-office-only tool.
- Real customers are frequently present and waiting during checkout and return, which is why speed and error-free billing matter more than exhaustive configurability.
- Inventory spans festival/cultural-event costumes and accessories (e.g. Garba/Navratri wear), weddings, and school/professional productions — demand is seasonal and can spike sharply around festival dates.
- A rental's lifecycle: item browse/availability check → customer registration or lookup → receipt creation (rent + deposit) → item given → return processing (late fee, damage assessment, deposit refund) → invoice.

## Capabilities and Constraints

- Two roles: OWNER (full access) and EXECUTIVE (counter operations only — no inventory writes, reports, or settings/config). This is an enforced product boundary, not just a UI convenience.
- Monetary values are always whole rupees — no paise/decimal handling anywhere in the product.
- Availability is tracked per physical unit within a category/item, not just per item type.
- Package items bill as a single line but reserve inventory for each underlying component independently.
- Editing an item's rate must never retroactively change pricing on already-created receipts (snapshot pricing at receipt time).

## Brand Commitments

Confirmed during init as final, not provisional: the shop name is **Manisha's Drapery**. The existing wordmark and logo (`frontend/public/logo.png`) are binding brand assets to preserve, not placeholders.

## Evidence on Hand

- `fashion-rental-discovery.md` is a real product discovery document (business goals, KPIs, user stories) produced for this project — treat it as authoritative product evidence, not filler.
- Inventory examples seen in local dev (Sherwanis, lehengas, character costumes, etc.) are realistic but are dev-seed data, not confirmed real catalog content — don't treat specific seeded item names/prices as durable facts.
- No marketing collateral, testimonials, case studies, or press exist. Do not fabricate any.

## Product Principles

1. **Billing correctness is non-negotiable.** Every design or flow decision defers to eliminating calculation errors over speed of iteration.
2. **Design for interruption, not focus.** The primary user is mid-transaction with a customer waiting — flows must tolerate being fast, not deliberate.
3. **Role boundaries are real, not cosmetic.** OWNER vs. EXECUTIVE reflects actual trust/authority differences in the business, not just a permissions checkbox.
4. **Internal-first, but not disposable.** Built for one shop today; avoid decisions that would be needlessly painful to generalize if it's ever offered to other shops later.
5. **The tablet is the primary surface.** Desktop use exists, but the shared in-store Android tablet is the environment design decisions should be checked against first.
