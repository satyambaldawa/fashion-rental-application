---
target: ReceiptDetailPage.tsx (+ InvoiceDetailPage.tsx)
total_score: 19
max_score: 40
na_heuristics: 
p0_count: 2
p1_count: 2
target_identity: "file:/Users/satyambaldawa/satyam/fashion-rental-application/frontend/src/pages/receipts/ReceiptDetailPage.tsx"
target_fingerprint: "sha256:a3c612fac6460bef221b4fbc6e3b01e40897b58cfda39ba75e41a835130cc800"
target_path: /Users/satyambaldawa/satyam/fashion-rental-application/frontend/src/pages/receipts/ReceiptDetailPage.tsx
timestamp: 2026-09-18T18-14-58Z
slug: frontend-src-pages-receipts-receiptdetailpage-tsx
---
# Design Critique — ReceiptDetailPage.tsx (+ InvoiceDetailPage.tsx)

Method: dual-agent (A: isolated sub-agent, full design review; B: detector run by parent (deterministic, equivalent to sub-agent) + live browser evidence gathered by parent directly)

## Design Health Score
Total: 19/40 (Poor)
1 Visibility of System Status 2/4
2 Match Between System and Real World 3/4
3 User Control and Freedom 2/4
4 Consistency and Standards 1/4
5 Error Prevention 1/4
6 Recognition Rather Than Recall 3/4
7 Flexibility and Efficiency of Use 2/4
8 Aesthetic and Minimalist Design 2/4
9 Error Recovery 1/4
10 Help and Documentation 2/4

## Design Specificity Verdict
Generic admin template with brand color on 2-3 isolated elements, not authored inside DESIGN.md's system. Heading is bare 20px Mulish (not eyebrow+serif+accent); status tag is antd stock blue; table has 0px radius (system floor 8px); deposit callout has correct Vivid Magenta border but antd info-blue background. Detector clean (0 findings) on both files — this drift class isn't markup-pattern detectable.

## What's Working
1. Deposit-refundable-on-return callout — genuine IA prioritization (wrong color, right placement)
2. Domain-accurate Terms & Conditions copy
3. Button-row wrap fix verified working cleanly at 390px

## Priority Issues
[P0] Line-items table hides Deposit/Rent/Line Deposit columns on mobile, zero scroll affordance — confirmed independently by both assessments (DOM: scrollWidth 660 vs clientWidth 342, no shadow/gradient cue). Contradicts app's own precedent (mobile nav rejected horizontal-scroll for the same reason). Fix: stacked item-cards on narrow viewports, or visible scroll cue + mini-summary. Suggested: layout
[P0] WhatsApp send fires immediately with no confirmation — real customer, real phone number, real total, zero undo. Fix: lightweight Popconfirm. Suggested: harden
[P1] Heading/status tag/table radius ignore the documented design system entirely (most-repeated screen in the app, zero brand treatment). Fix: apply eyebrow+serif heading, chip tokens on status tag, 14px table radius. Suggested: typeset
[P1] Deposit callout background (#E6F4FF, antd default) clashes with warm palette; ~6 hardcoded hex values scattered with no token reference. Fix: Petal Pink/Blush Mist background. Suggested: colorize
[P2] Touch targets 32px, confirmed independently by both assessments — below tablet's 44px rationale, especially Process Return. Suggested: polish
[P3] Loading/error states are bare unstyled text, error is a dead end with no recovery. Suggested: harden

## Persona Red Flags
Alex: hidden mobile columns require remembering to scroll; zero efficiency gain from daily repeat visits.
Riley: untested long-name/many-item receipts (seed data limitation, flagged not asserted); confirmed "Receipt not found" is a dead end.
Casey: WhatsApp button in tight 12px-gap row at 32px height, thumb-zone mis-tap risk compounding the P0; horizontal-scroll table has no discovery cue.

## Minor Observations
- formatCategory: '—' on Receipt vs '' on Invoice (small inconsistency)
- InvoiceDetailPage's financial layout (split Rent/Return-settlement blocks) is better IA than Receipt's flat list — worth borrowing
- InvoiceDetailPage's refund/collect callout correctly uses semantic green/red per DESIGN.md's carve-out
- Every fix applies to both files — InvoiceDetailPage duplicates the same button row, touch targets, and lack of brand treatment
- Untested: empty-line-items receipt, full screen-reader pass on print CSS

## Questions to Consider
1. Should Vivid Magenta + serif accent be the only two things allowed to appear on this screen?
2. Should Process Return be a separate button, or should the deposit callout itself become the tappable CTA?
3. Is "desktop-first table pattern never rethought for tablet" true of other secondary screens too?
