# BentoBar 1.3: the visual spec (glass, shadow, strip highlight)

## Why this exists in plain English

Today's popups are solid cards whose Android shadow leans away from the screen's middle and is cut off at the window's
edge: the "off to one side" look. In 1.3 each popup is a small sheet of Booklight's glass. The desktop shows through,
blurred, under a veil thick enough for small text over a white page or a black terminal. We draw the shadow ourselves:
straight under the card, heavier at the bottom, never seen through the glass. In the strip, one quiet pill replaces
each item's own hover box, and the pointer carries it from item to item.

Artboards: `Glass` (with a lever to compare Booklight's veil), `Shadow`, `StripLook`; 1 px = 1 dp.

## 1. Glass

| | Light | Dark | Why |
|---|---|---|---|
| Blur | 24 dp (Android radius; σ ≈ 14) | 24 dp | A touch above Booklight's 22: popups sit over window chrome and small text that must blur past reading. |
| Veil | `surfaceContainerLowest` × 0.66 | × 0.72 | 12–14 sp text, not Booklight's 17 sp titles (contrast below). Dark gets more: white pages are the usual thing behind it. |
| Corner radius | 24 dp | 24 dp | Concentric with the 12 dp rows and 10 dp header tile, 12–14 dp in (today 20). |
| Rim | white, 1 dp, × 0.80 | × 0.44 | Booklight's outline, thinned from 1.25 dp for a small card. |
| Hairline | black, outermost 1 px, × 0.20 | × 0.28 | Holds the edge on a white page. Same values as Booklight. |
| Solid fallback | `surfaceContainerHigh`, opaque | same | Booklight's. Rim, hairline, radius, shadow stay: the same object without blur. |
| Screen dim | none | none | A menu is not a modal. |

**One level, no setting:** a menu is glanced at; one value tuned for the hard cases beats a choice nobody revisits.
The rim stays still (no travelling reflection).

**Contrast.** The worst case is a flat field; a busy backdrop blurs towards grey, which only helps. Saturated wallpapers
land between (light over #1A3BA0: labels 5.0:1; dark over #FFD600: 5.5:1).

| Behind the glass | Titles, values (`onSurface`) | Labels (`onSurfaceVariant`) | Section labels (`primary`) |
|---|---|---|---|
| Light glass, pure black | 7.2:1 | 3.9:1 | 2.7:1 |
| Light glass, dark window #202124 | 8.2:1 | 4.5:1 | 3.1:1 |
| Dark glass, pure white | 6.1:1 | 4.6:1 | 4.6:1 |
| Booklight's 0.42 / 0.50, same three | 3.2 / 4.2 / 2.8 | 1.8 / 2.3 / 2.1 | 1.2 / 1.6 / 2.1 |

The weak spot, section labels on light glass over pure black, is short uppercase text that repeats its rows: accept,
keep the colour.

## 2. Shadow (drawn by us, not Android's elevation)

Two black layers, drawn **only outside the card's rounded rectangle** (clip the card's shape out, as Booklight's
`PanelOutline` clears it). **No horizontal offset**, wherever the popup sits.

| Layer | Offset y | Blur σ (CSS blur) | Spread | Light | Dark |
|---|---|---|---|---|---|
| key | 7 dp | 9 dp (18) | −4 dp | 0.12 | 0.20 |
| contact | 1 dp | 1 dp (2) | 0 | 0.04 | 0.07 |

The −4 dp spread tucks the key in at the sides and top; the offset puts it under the lower edge (light from above).
Skia `BlurMaskFilter` radius ≈ (σ − 0.5) / 0.577: 14.7 dp and 0.9 dp.

**Darkening** (black's alpha just outside the edge, on white):

| | Lower edge | Sides | Top | Below 1% at |
|---|---|---|---|---|
| Booklight Low (measured) | 15% | ~7% | 2–3% | 30 dp below |
| 1.3 light | 10% | 6% | 2% | 16 dp below, 9 beside, 2 above |
| 1.3 dark | 18% | 10% | 3% | 18 dp below, 11 beside, 4 above |

Dark is about 1.7 × light: a dark window darkened 10% shows nothing. There the 0.44 rim does most of the separating.

**Room:** the window's margin goes from 12 dp all round to **top 8, sides 16, bottom 24 dp**; the shadow is under
0.5% there, so nothing is cut. The card stays 4 dp under the bar; the window covers 4 dp less of the bar.

## 3. Strip highlight

| | Value | Why |
|---|---|---|
| Shape | Stadium, **26 dp** tall (30 dp item less 2 + 2), radius 13 | Echoes the strip pill; inside one, an even 2 dp ring at its ends. |
| Extent | Item bounds (with their 6 dp padding) + **4 dp each end**: content 10 dp from the ends | Round ends need side room. ‹ alike, at least 26 dp wide. |
| Hover | text colour (`look.fg`) × **0.14** | Today's value; one value for every strip background and light or dark bars. |
| Held (popup open) | fg × **0.18**; the pill stays on that item | A shade firmer: the popup is visibly attached. |
| Pressed | fg × **0.22** | Booklight's pressed step (+0.08). |
| Border | none | The status bar is the system's; a rim there is fuss. |
| Alert item | Its red capsule (`alertBg`/`alertFg`) becomes **22 dp** tall, radius 11, item + 2 dp each end; the pill passes under it, showing as a 2 dp ring | Same pill on every item; the red stays the item's, calmer than today's 30 dp box. |
| Lifted (dragging) | The tile takes the pill's shape, today's colour; the pill hides | |

On the 12% strip pill the highlight adds up to about 0.24: even steps from bar to pill to highlight. Layers: strip
pill, highlight, alert capsule, content. A held slider keeps the pill on its item at hover strength.

## 4. Inside the popups (small changes for the glass)

- **`MenuEntry` hover:** `onSurface` 0.08 → **0.10**, to hold over glass whose brightness varies across the card.
- **Wells:** `Sparkline` and `Meter` tracks and `ActionTile` at rest go from `surfaceContainerHighest`/`High` to
  `onSurface` × **0.06 light / 0.08 dark** (tile hover 0.12 / 0.14). Opaque grey patches on glass look like stickers.
- **`MenuDivider`:** `outlineVariant` → `onSurface` × **0.12 / 0.16**; over a dark window `outlineVariant` comes out
  lighter than the glass.

**Stays the same:** widths, `MenuCard` padding (14 × 12), 40 dp rows with 12 dp corners, type, icons, text colours, the
`primaryContainer` header tile and `secondaryContainer` chips, `SearchField`, focus rings; popup position (4 dp under
the bar, edge-aligned) and maximum height; the strip's layout (12 dp gaps, 6 dp padding), its pill's shape and fills,
tooltips, the 1.08 lift.

## Tokens

```
glass.blur            24 dp (Android radius; CSS σ ≈ 14)
glass.veil            surfaceContainerLowest × 0.66 light / 0.72 dark
glass.solid           surfaceContainerHigh × 1.0
glass.radius          24 dp
glass.rim             #FFFFFF, 1 dp, × 0.80 light / 0.44 dark
glass.hairline        #000000, outermost 1 px, × 0.20 light / 0.28 dark
shadow.key            y 7 · σ 9 (CSS blur 18) · spread −4 · #000 × 0.12 light / 0.20 dark
shadow.contact        y 1 · σ 1 (CSS blur 2) · spread 0 · #000 × 0.04 light / 0.07 dark
shadow.room           top 8 · sides 16 · bottom 24 dp (window margin)
menu.rowHover         onSurface × 0.10
menu.well             onSurface × 0.06 light / 0.08 dark   (hover 0.12 / 0.14)
menu.divider          onSurface × 0.12 light / 0.16 dark
strip.hl.height       26 dp      strip.hl.radius   13 dp     strip.hl.outset   4 dp
strip.hl.hover        fg × 0.14  strip.hl.held     fg × 0.18 strip.hl.pressed  fg × 0.22
strip.alert           alertBg · 22 dp tall · radius 11 · outset 2 dp
```

The artboards use these as CSS variables (`--glass-veil-light`, `--shadow-key-dark`, `--strip-hl-held`, …).
