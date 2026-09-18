---
name: Manisha's Drapery
description: A festive-heritage counter tool for a costume rental shop — maroon and rose, one serif flourish, built for fast tablet transactions.
colors:
  rani-rose: "#A81259"
  wine-maroon: "#6E0B37"
  petal-pink: "#EAB9CF"
  vivid-magenta: "#C2185B"
  blush-mist: "#FBF1F5"
  dusty-petal: "#eed6e0"
  muted-mauve: "#7a5361"
  deep-plum: "#33101F"
typography:
  display:
    fontFamily: "Cormorant Garamond, Georgia, serif"
    fontSize: "36px"
    fontWeight: 500
    lineHeight: 1.1
    letterSpacing: "normal"
  label:
    fontFamily: "Jost, system-ui, sans-serif"
    fontSize: "11px"
    fontWeight: 600
    lineHeight: 1.2
    letterSpacing: "0.18em"
  nav:
    fontFamily: "Jost, system-ui, sans-serif"
    fontSize: "13px"
    fontWeight: 500
    lineHeight: "22px"
    letterSpacing: "0.01em"
  body:
    fontFamily: "Mulish, system-ui, sans-serif"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: 1.5
rounded:
  sm: "8px"
  md: "14px"
  full: "999px"
spacing:
  sm: "8px"
  md: "16px"
  lg: "24px"
components:
  button-primary:
    backgroundColor: "{colors.rani-rose}"
    textColor: "#ffffff"
    rounded: "{rounded.sm}"
  chip-active:
    backgroundColor: "{colors.wine-maroon}"
    textColor: "#ffffff"
    rounded: "{rounded.full}"
    padding: "5px 16px"
  chip-inactive:
    backgroundColor: "#ffffff"
    textColor: "{colors.muted-mauve}"
    rounded: "{rounded.full}"
    padding: "5px 16px"
  nav-pill-active:
    backgroundColor: "{colors.petal-pink}"
    textColor: "{colors.wine-maroon}"
    rounded: "{rounded.full}"
    padding: "6px 16px"
  nav-pill-inactive:
    backgroundColor: "transparent"
    textColor: "rgba(255,255,255,0.85)"
    rounded: "{rounded.full}"
    padding: "6px 16px"
  card:
    backgroundColor: "#ffffff"
    rounded: "{rounded.md}"
---

# Design System: Manisha's Drapery

## Overview

**Creative North Star: "The Festive Ledger"**

This is an operational tool wearing exactly one ornament: a rich, warm heritage palette (wine maroon, rani rose, petal pink) lifted from the traditional-wear domain it serves, paired with a single serif italic flourish (Cormorant Garamond) on page headings. Everything else in the system is composed and restrained — Mulish for body text, generous whitespace, one accent color used sparingly, fully-rounded tactile controls sized for a shared in-store tablet. The metaphor is a shopkeeper's ledger dressed for a festival: warm and personal at a glance, disciplined and fast the moment you actually use it.

Confirmed mood: warm & festive, composed & restrained, editorial (never corporate-SaaS, never garish or ornamental beyond the one serif accent).

**Key Characteristics:**
- One warm heritage palette, used with restraint — rani rose is the only color that means "active" or "action."
- A single serif italic accent word per page heading; everything else is a clean sans.
- Fully-rounded, tactile controls (pills, buttons) sized for a tablet, not a mouse.
- Shadows are soft, tinted from the maroon hue, and used sparingly — never neutral black.

## Colors

Warm and heritage-rooted, built around one accent used deliberately rather than a broad multi-color system.

### Primary
- **Rani Rose** (#A81259): The one color that means "this is active, selected, or actionable." Buttons, links, active filter chips on light backgrounds, the italic accent word in every page heading.

### Secondary
- **Petal Pink** (#EAB9CF): The active-state color specifically on dark (wine maroon) surfaces — the mobile nav's selected pill, where rani rose would lose contrast against maroon.
- **Wine Maroon** (#6E0B37): The app's one deep surface color — header, nav, sidebar backgrounds. Also doubles as the active chip background on light surfaces (see Chips).

### Tertiary
- **Vivid Magenta** (#C2185B): Reserved for rare, singular high-emphasis callouts (a receipt number, a modal's primary CTA) — not a general-purpose accent. Using it in more than one place per screen dilutes the emphasis it exists for.

### Neutral
- **Blush Mist** (#FBF1F5): Page background.
- **Dusty Petal** (#eed6e0): Default borders — input borders, inactive chip borders, card borders.
- **Muted Mauve** (#7a5361): Secondary text and icons — eyebrow counts, placeholder icons, inactive chip labels.
- **Deep Plum** (#33101F): Primary heading text — near-black but warmed toward the palette, never true black.

Semantic feedback colors (success/error/warning) use antd's defaults (`#52c41a` / `#ff4d4f` / `#fa8c16`) rather than custom tokens — the palette above is reserved for brand and interaction state, not system feedback.

### Named Rules
**The One Accent Rule.** Rani Rose is the only color that means "active" on a light surface. It is not used decoratively — if something is rose, it is either selected, actionable, or the one italic word in a heading.

## Typography

**Display Font:** Cormorant Garamond (with Georgia, serif fallback)
**Body Font:** Mulish (with system-ui, sans-serif fallback)
**Label/Nav Font:** Jost (with system-ui, sans-serif fallback)

**Character:** A serif/sans pairing where the serif appears exactly once per screen (the page heading) and everything else — labels, navigation, body copy, data — stays in the clean, fast-reading Jost/Mulish sans pairing. The serif is a flourish, not a voice used throughout.

### Hierarchy
- **Display** (weight 500, 36px, line-height 1.1): Page headings only, e.g. "Browse *Items*" — plain lead word in Deep Plum, one italic accent word in Rani Rose.
- **Label** (weight 600, 11px, letter-spacing 0.18em, uppercase): The eyebrow line above a page heading (e.g. "NEW RENTAL"), and other small uppercase metadata labels.
- **Nav** (weight 500, 13px, letter-spacing 0.01em): Navigation items and pill/chip labels specifically — both the desktop horizontal menu and the mobile wrapping pill nav.
- **Body** (weight 400, 14px): Default UI text — antd's base font size, used for everything not covered above.

### Named Rules
**The One Serif Rule.** Cormorant Garamond appears only in page-heading display type. It never appears in body copy, labels, navigation, or data — the moment it shows up twice on a screen, the flourish stops reading as intentional.

## Layout

Content is centered in a max-width 1180px container with 24px padding (`AppLayout`), sitting on the Blush Mist page background. Below the `lg` (992px) breakpoint — the shared in-store tablet's working width — the header splits from one 64px row into two stacked rows (brand row, then a wrapping nav row), and navigation itself switches from a horizontal antd Menu to a wrapping row of pills so every item stays visible without a scroll or swipe gesture. Desktop and mobile are two distinct, deliberately different layouts for the header/nav — not a single layout that just reflows.

## Elevation & Depth

Confirmed: shadows are a restrained, ambient grounding cue, not a layered elevation system with multiple depth levels. They appear in exactly two places — the sticky header and hoverable cards — and are always tinted from the wine maroon brand hue rather than neutral black or gray.

### Shadow Vocabulary
- **Sticky bar** (`box-shadow: 0 2px 8px rgba(110,11,55,0.25)`): Grounds the header/nav bar against scrolled content beneath it.
- **Lifted card** (`box-shadow: 0 10px 30px -18px rgba(110,11,55,0.4)`): A soft, wide, low-opacity lift on hoverable surfaces (item cards, the login card) — diffuse rather than sharp.

### Named Rules
**The Tinted Shadow Rule.** Every shadow in the system is tinted from wine maroon (`rgba(110,11,55,…)`). A neutral black/gray shadow anywhere in this system is a bug, not a style choice.

## Shapes

Tactile and rounded throughout — sized for finger touch on a shared tablet, not just mouse precision. Three radius steps cover the whole system: 8px for buttons and inputs, 14px for cards and tables, and fully-rounded (999px) for every chip, filter pill, and nav pill. There is no sharp-cornered surface anywhere in the system; the sharpest corner in the app is 8px.

## Components

### Buttons
- **Shape:** 8px radius (`{rounded.sm}`), consistent across primary and secondary variants.
- **Primary:** Rani Rose (#A81259) background, white text — antd's `colorPrimary` token, so this is the default for every unstyled primary button in the app.
- **Secondary / Danger:** antd defaults, same 8px radius.

### Chips / Pills
Two distinct treatments depending on the surface they sit on — this is the system's signature recurring pattern, used for category filters, type filters, and both desktop and mobile navigation.

- **On light surfaces** (checkout category filters, gallery filters): fully-rounded, `5px 16px` padding, Jost 500 13px. Inactive: white background, Dusty Petal border, Muted Mauve text. Active: Wine Maroon background, Rani-Rose-tinted border, white text.
- **On dark surfaces** (mobile nav pills, against the Wine Maroon header): fully-rounded, `6px 16px` padding, 44px minimum height (a deliberate touch-target floor — smaller pills risked mis-taps between wrapped rows on the tablet). Inactive: transparent background, `rgba(255,255,255,0.3)` border, `rgba(255,255,255,0.85)` text. Active: Petal Pink background, Wine Maroon text — Rani Rose is skipped here because it doesn't carry enough contrast against maroon.
- **Focus:** mobile nav pills carry an explicit `:focus-visible` outline (2px, Petal Pink, 2px offset) — the browser default is invisible against this surface, so it's drawn explicitly.

### Cards / Containers
- **Corner Style:** 14px radius (`{rounded.md}`).
- **Background:** White.
- **Border:** 1px Dusty Petal.
- **Shadow Strategy:** the Lifted Card shadow (see Elevation & Depth) on hover.
- **Transition:** `transform 0.15s ease, box-shadow 0.15s ease` — a quick, subtle lift, not a dramatic one.

### Navigation
- **Desktop** (≥992px): a single-row antd horizontal `Menu`, dark theme, on the Wine Maroon header. Active item gets a 3px Petal-Pink-tinted underline, not a filled background.
- **Mobile** (<992px): the wrapping pill nav described above under Chips / Pills — every item visible at once, no scroll/swipe required to discover items past an edge. This was a deliberate rejection of a horizontal-scroll nav pattern tried earlier, on the grounds that requiring a gesture to discover nav items is worse than showing all of them for a tool used many times a day.

## Do's and Don'ts

### Do:
- **Do** keep Rani Rose as the only "this is active/actionable" signal on light surfaces; introducing a second color for the same meaning dilutes it.
- **Do** tint every shadow from wine maroon (`rgba(110,11,55,…)`) — never a neutral gray/black shadow.
- **Do** use the dark-surface pill treatment (Petal Pink active, translucent-white inactive, 44px min-height) for anything placed on the Wine Maroon header; the light-surface treatment loses contrast there.
- **Do** keep the serif (Cormorant Garamond) confined to the display heading's single italic accent word — it is a flourish, not a body voice.

### Don't:
- **Don't** add a second accent color for "active" state on light surfaces — Rani Rose already owns that meaning.
- **Don't** use Vivid Magenta (#C2185B) more than once per screen — it exists for rare, singular emphasis, not as a general accent.
- **Don't** give any pill/chip/nav-item a touch target under 44px on the mobile surface — the tablet is tapped constantly and mis-taps between wrapped rows are a real, confirmed failure mode here.
- **Don't** introduce a sharp (0px) or heavily rounded (>14px, short of full pill) corner — the system's entire radius vocabulary is 8 / 14 / full, nothing else.
