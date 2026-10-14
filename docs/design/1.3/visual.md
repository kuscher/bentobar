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
| Veil | `surfaceContainerLowest` × **0.56** | × **0.62** | Alex asked for more see-through (8 Oct; was 0.66 / 0.72). Still above Booklight's 0.42 / 0.50 for 12–14 sp text; dark gets more, white pages being the usual thing behind it. Needs the glass inks below. |
| Corner radius | 24 dp | 24 dp | Concentric with the 12 dp rows and 10 dp header tile, 12–14 dp in (today 20). |
| Rim | white, 1 dp, × 0.80 | × 0.44 | Booklight's outline, thinned from 1.25 dp for a small card. |
| Hairline | black, outermost 1 px, × 0.20 | × 0.28 | Holds the edge on a white page. Same values as Booklight. |
| Solid fallback | `surfaceContainerHigh`, opaque | same | Booklight's. Rim, hairline, radius, shadow stay: the same object without blur. |
| Screen dim | none | none | A menu is not a modal. |

**One level, no setting:** a menu is glanced at; one value tuned for the hard cases beats a choice nobody revisits.
The rim stays still (no travelling reflection).

**Contrast.** The worst case is a flat field; a busy backdrop blurs towards grey, which only helps, and saturated
wallpapers land between the cases below.

At 0.56 / 0.62 the scheme's own inks drop too far (dark titles over a white page 4.2:1, light grey labels over black
2.9:1, section labels 2.0:1). So **on glass only** (blur on; the solid fallback keeps the scheme's inks):

- grey text (`onSurfaceVariant`) and section labels (`primary`) are drawn **halfway to `onSurface`** (`lerp` 0.5:
  about tone 20 / 25 in light, 85 in dark);
- in dark, titles and values (`onSurface`) are drawn **halfway to white** (about tone 95).

| Behind the glass, with those inks | Titles, values | Grey labels | Section labels |
|---|---|---|---|
| Light glass, pure black | 5.3:1 | 4.1:1 (was 2.9) | 3.4:1 (was 2.0) |
| Light glass, dark window #202124 | 6.3:1 | 4.8:1 | 4.1:1 |
| Dark glass, pure white | 4.8:1 (was 4.2) | 3.7:1 (was 3.2) | 3.7:1 |
| Booklight's 0.42 / 0.50, its own inks | 3.2 / 4.2 / 2.8 | 1.8 / 2.3 / 2.1 | 1.2 / 1.6 / 2.1 |

Grey labels over pure white in dark (3.7:1) are the floor this veil allows; the halfway step keeps their hue.

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

- **`MenuEntry` hover:** `onSurface` 0.08 → **0.12** (0.10 at the old veil), to hold over thinner glass whose brightness
  varies across the card.
- **Wells:** `Sparkline` and `Meter` tracks and `ActionTile` at rest go from `surfaceContainerHighest`/`High` to
  `onSurface` × **0.08 light / 0.10 dark** (tile hover 0.14 / 0.16; raised with the thinner veil). Opaque grey
  patches on glass look like stickers.
- **`MenuDivider`:** `outlineVariant` → `onSurface` × **0.12 / 0.16**; over a dark window `outlineVariant` comes out
  lighter than the glass.

**Stays the same:** widths, `MenuCard` padding (14 × 12), 40 dp rows with 12 dp corners, type, icons, text colours, the
`primaryContainer` header tile and `secondaryContainer` chips, `SearchField`, focus rings; popup position (4 dp under
the bar, edge-aligned) and maximum height; the strip's layout (12 dp gaps, 6 dp padding), its pill's shape and fills,
tooltips, the 1.08 lift.

## Tokens

```
glass.blur            24 dp (Android radius; CSS σ ≈ 14)
glass.veil            surfaceContainerLowest × 0.56 light / 0.62 dark
glass.ink2            on glass: onSurfaceVariant, primary text → lerp(·, onSurface, 0.5)
glass.ink1Dark        on dark glass: onSurface → lerp(onSurface, white, 0.5)
glass.solid           surfaceContainerHigh × 1.0
glass.radius          24 dp
glass.rim             #FFFFFF, 1 dp, × 0.80 light / 0.44 dark
glass.hairline        #000000, outermost 1 px, × 0.20 light / 0.28 dark
shadow.key            y 7 · σ 9 (CSS blur 18) · spread −4 · #000 × 0.12 light / 0.20 dark
shadow.contact        y 1 · σ 1 (CSS blur 2) · spread 0 · #000 × 0.04 light / 0.07 dark
shadow.room           top 8 · sides 16 · bottom 24 dp (window margin)
menu.rowHover         onSurface × 0.12
menu.well             onSurface × 0.08 light / 0.10 dark   (hover 0.14 / 0.16)
menu.divider          onSurface × 0.12 light / 0.16 dark
strip.hl.height       26 dp      strip.hl.radius   13 dp     strip.hl.outset   4 dp
strip.hl.hover        fg × 0.14  strip.hl.held     fg × 0.18 strip.hl.pressed  fg × 0.22
strip.alert           alertBg · 22 dp tall · radius 11 · outset 2 dp
```

The artboards use these as CSS variables (`--glass-veil-light`, `--shadow-key-dark`, `--strip-hl-held`, …).

## 5. Contents appearing (8 October)

Timing and order are the motion designer's; this is how each part looks on its way in.

- **Allowed:** layer properties (alpha, translation, scale) and draw-phase trims, clips and fill widths. No re-measured
  text, no blur, no colour change beyond a fill's alpha, nothing past its end state, nothing outside the growing glass.
- **Text moves down into place,** with the glass opening downward: 6 dp for rows, 4 dp for small text. Objects (tiles,
  chips, artwork, discs) scale from their centre instead of travelling.
- **The end state is the final look exactly.** A row under a resting pointer takes its hover tint only after it lands,
  with the hover's own fade.
- **Light and dark use the same values;** fading text passing through a paler (or dimmer) ink needs no compensation.
- **No animations** (the new setting, or animator scale 0): every part at its end state at once, no fade.
- **Anything not listed** appears as a row.

| Role | Start | End | Notes |
|---|---|---|---|
| Header icon tile | scale 0.84, fill alpha 0, glyph alpha 0 | 1 · 1 · 1 | The `primaryContainer` fill rises over the glass (the tint arriving); the glyph follows once the fill is past half |
| Title, subtitle | alpha 0, y −4 dp | alpha 1, y 0 | The subtitle a beat after the title |
| Big value ("70°") | alpha 0, y −6 dp, scale 0.96 from its left baseline | 1 · 0 · 1 | No counting up; the lines beside it as rows |
| `InfoRow` | alpha 0, y −6 dp | 1 · 0 | Label and value as one |
| `SectionLabel` | alpha 0 | 1 | No travel; lands a beat before its first row |
| `MenuEntry` | alpha 0, y −6 dp | 1 · 0 | Icon, label, detail as one; a disabled row lands at its 0.38 ink |
| `MenuNote`, `MenuDivider` | alpha 0 | 1 | No travel; the divider does not draw across |
| `Sparkline` | well alpha 0; line trimmed to 0; fill clipped at x 0 | well 1, trim 1, clip full | Well first; the line draws left to right (oldest to newest), the fill under it clipped to the line's head at its resting gradient; a second series draws with the first |
| `HourStrip` column | alpha 0, y −6 dp | 1 · 0 | Time, icon and temperature as one; left to right |
| `DayRow` | alpha 0, y −6 dp | 1 · 0 | As a row; a range bar, where shown, grows like a meter |
| `TileGrid` tile | alpha 0, scale 0.92 | 1 · 1 | Its well and label as one |
| `ChipRow` chip | alpha 0, scale 0.92 | 1 · 1 | The ✓ with it; its colour never changes |
| `Meter`, `CoreBars` | track alpha 0; fill width (bars: height) 0 | track 1, fill at the value | Track first, then the fill grows from its start (bars from the bottom) and stops exactly on the value |
| Month grid | weekday row and each week: alpha 0, y −4 dp | 1 · 0 | Weeks as rows, not 42 cells; event dots ride with their week |
| Today's disc | alpha 0, scale 0.6 | 1 · 1 | After its week lands; its number, in the disc's on-colour throughout, scales and fades with it |
| `MediaMenu` artwork | alpha 0, scale 0.96 | 1 · 1 | Clipped to its rounded corners throughout; track text as rows |
| Flight route line | rest line alpha 0; flown part trimmed to 0; plane alpha 0, 4 dp back along the line | all at rest | Rest line and dots first; the flown part draws from the origin; the plane settles forward last |
| `SearchField` | alpha 0 | 1 | No travel: the caret is live at once |

Shared values for the motion designer: rows −6 dp, small text −4 dp, objects 0.92, tile 0.84, artwork and big value
0.96, disc 0.6; every start alpha 0.

## 6. OS tint (option, off by default)

A switch in Look ("Tint with system colours", off by default) gives the popup glass a little of the wallpaper's own
palette, the way macOS tints windows. It uses the **secondary** palette, not primary: it comes from the same wallpaper
but has about half the chroma, so the result is a tint rather than a colour wash. Each tint colour sits close to the
veil's own tone (tone 90 mixed with white, tone 20 mixed with tone 4), so the change in brightness is small. A +0.04
veil alpha recovers the rest.

| Token | Light | Dark | Notes |
|---|---|---|---|
| `glass.tint.veil` | lerp(`surfaceContainerLowest`, `secondaryContainer`, **0.5**) × **0.60** | lerp(`surfaceContainerLowest`, `onSecondary`, **0.5**) × **0.66** | Off: 0.56 / 0.62 neutral. Over a white page or a dark window, a 8–19 / 8–33 sRGB-level tint |
| `glass.tint.solid` | lerp(`surfaceContainerHigh`, `secondaryContainer`, 0.5) | lerp(`surfaceContainerHigh`, `onSecondary`, 0.5) | Opaque; text keeps ≥ 7:1 |
| rim, hairline, shadow | unchanged | unchanged | Neutral white and black: a coloured edge or shadow is the gimmick to avoid |
| row hover, wells, dividers | unchanged | unchanged | `onSurface` tints, so they take on the hue beneath them |
| glass inks (§1) | unchanged | unchanged | The +0.04 alpha keeps their contrast |
| header tile, chips | unchanged | unchanged | Chips read as a deeper step of the same tint |
| strip pill | **not tinted** | **not tinted** | It sits on the system's bar, beside untinted system icons and over any bar colour. A tinted pill would read as a state or accent, so it stays `look.fg` × 0.14 / 0.18 / 0.22 |

**Contrast with the tint on**, at the most saturated palettes tried (vibrant red, green, blue and orange) and the §1
glass inks:

| | Titles | Grey labels | Section labels |
|---|---|---|---|
| Light glass, over pure black | 5.4:1 | 4.1:1 | 3.5:1 |
| Dark glass, over pure white | 4.7–4.8:1 | 3.6–3.7:1 | 3.6–3.7:1 |

At the untinted 0.56 / 0.62 the tint alone would cost about 0.5:1 (dark titles 4.1:1). Hence the +0.04.
