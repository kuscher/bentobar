# BentoBar 1.3: motion

*Motion designer, 8 October 2026. Proposed. Prototypes: `Opening.dc.html`, `Highlight.dc.html` (1.3 canvas).*

## Why this exists in plain English

Today a popup appears with a small 140 ms fade, and each strip item lights up on its own with nothing moving
between them. Alex asked for popups that unfold down from the bar with their contents settling into place, and
for one highlight that follows the pointer across the strip like Booklight's rubber-band rows. This page gives
every number for both. It reuses Booklight's approved curves and springs wherever they fit, so Alex can judge the
prototypes before anything is built.

## 1. Opening

*Version 2 (§7) replaces the numbers in §1–4 wherever it gives new ones.*

| Part | Value | Why |
| --- | --- | --- |
| Window | added at full size, laid out once | the glass is uncovered, not scaled |
| Clock | starts at the first drawn frame | a slow layout must not eat the start |
| Lip | 20 dp tall, full width, edge-aligned with its item | something shows within two frames |
| Width | full from the start | two axes reads as zooming; the aligned edge ties it to the item |
| Height | 20 dp → H on cubic-bezier(0.55, 0, 0.1, 1) | Booklight's approved slow-fast-slow, no overshoot |
| Duration T | 240 ms + 20 ms per 100 dp over 200, at most 340 (H 300: 260; 700: 340) | a fixed time moves a 700 dp edge 2.3× faster than Booklight's glass; a fixed speed takes 0.6 s |
| Presence (glass, veil, outline) | 0 → 1 in 100 ms, cubic-bezier(0.2, 0, 0, 1) | arrives during the slow start: no pop |
| Corners | min(20 dp, height ÷ 2) | the lip is a 10 dp capsule like the pill, a card by 40 dp |
| Blur | the glass's own region every frame; radius × presence, ≥ 1 px | Booklight's "the blur follows the glass" |
| Shadow | the glass's outline; strength × presence × smoothstep(0, 0.6) of openness | full strength under a 20 dp lip draws a dark line |

**Timeline**, H = 300 dp, T = 260 ms:

| ms | On screen |
| --- | --- |
| 0 | lip 20 dp at presence 0; the item's pill is on |
| 17 | lip visible (0.42) |
| 30 | 25 dp; header starts dropping in |
| 60 | 50 dp, header uncovered; shadow begins |
| 80 | 96 dp: the fast middle |
| 100 | 175 dp; shadow full; header readable |
| 140 | 255 dp; rows 3–5 arriving |
| 190 | 289 dp; last row starts |
| 260 | glass at rest |
| ~240 / ~330 | last row readable / all within 0.5 dp |

## 2. Contents moving into place

| Part | Value | Why |
| --- | --- | --- |
| Direction | **drop**: 8 dp from above | it travels with the edge; a rising row crosses it (the prototype has Rise) |
| Block | each child of the card: header, Sparkline, InfoRow, MenuEntry, SectionLabel, a TileGrid row, ChipRow, Meter; a divider joins the block below | one rule; tiles in a row share a step, no sideways ripple |
| Start | block i of n at 30 + i × min(20, 160 ÷ (n − 1)) ms | 20 ms apart up to nine blocks; in a taller popup closer together, the last still at 190 ms and none together (revised after the review of the build: a cap of 8 steps clumped the lower rows) |
| Motion | spring(0.86, 520), Booklight's `place` | overshoots 0.04 dp: nothing shrinks back |
| Alpha | smoothstep(0, 0.6) of the block's own progress | readable ~65 ms in while the last 3 dp settle; the clip already hides what the edge hasn't reached |
| Inside a block | nothing animates | opened fifty times a day, it must not perform |

## 3. Closing and interruptions

| Part | Value | Why |
| --- | --- | --- |
| Touches | pass through from the first frame (one flags update) | the next click never lands on a leaving popup |
| Contents | fade together in place, 50 ms linear | the edge must not slice text |
| Glass | folds to the lip on cubic-bezier(0.45, 0, 0.4, 1) in F = 0.45 × T (108–153 ms) | Booklight's fold, back into its item |
| Presence | 1 → 0 over 60 ms, from 30 ms before the fold ends | the lip fades as it lands |
| Total | F + 30 (147 ms at 300 dp; Booklight 155), then `removeViewImmediate` | |

A plain fade is quieter but loses the tie to the item.

**Timeline**, H = 300: 0 ms contents fade, edge sets off · 17: 288 dp · 50: 170 dp, contents gone ·
87: 40 dp, presence falling · 117: lip at 0.5 · 147: gone.

| Interruption | What happens |
| --- | --- |
| Same item while opening | folds from where it is in F × openness (≥ 50 ms) |
| Clicked while closing (the 350 ms guard against the closing press stays) | reopens from where it is on spring(1.0, 1000), keeping the edge's speed; presence back in 60 ms; contents fade back in place in 100 ms |
| Another item | the old popup dissolves in place in 90 ms (no fold); the new one opens fully; the pill glides over. Fold-then-open costs 400 ms per switch, with two edges moving opposite ways |
| Esc, outside touch, an entry's action | close as above |
| Height changes while open | spring(0.86, 520) from where it is |

## 4. The strip highlight

| Part | Value |
| --- | --- |
| Pill | the item's box (item + 6 dp each end), 30 dp tall, radius 10 |
| Leading edge | spring(0.85, 1400), at once |
| Old edge | holds 5 frames at 120 Hz (2 at 60), then spring(0.86, 900) |
| Long way | either edge has over **110 dp** to go: holds 2 frames, then spring(0.90, 1400). Booklight uses 98; 110 makes every neighbour step (36–102 dp) near, except from a wide item to a narrow one, where the old edge crosses the wide item (revised after the review of the build) |
| Stretch | as is up to 40 dp past the wider item, eased to at most 56 |
| Already moving | no hold; each edge carries on from where it is at its speed |
| Simulated, 120 Hz | 58 → 88 dp: 35 dp stretch, at rest 200 ms. 90 → 90: 52 dp, 258 ms. Booklight's row, same code: 34 dp, 200 ms (measured 34, 197) |

**Rules**

- **Which item:** split at each gap's middle, crossed 2 dp past it; end items own out to the strip's ends. The
  pill never drops out in a gap, and a click in a gap goes to the highlighted item. ‹ is one more item.
- **Entering:** fades in on the hovered item, 120 ms, cubic-bezier(0.4, 0, 0.2, 1). No slide.
- **Leaving:** 150 ms grace (a pointer slipping below the bar while scrubbing), then fades where it stands, 120 ms.
  Back during the fade: fades up and glides from where it is.
- **Popup open:** stays on the popup's item and doesn't follow hover (question 1). Clicking another item glides
  it there. After closing it waits for the pointer to move, or fades with the fold if the pointer is elsewhere.
- **Dragging:** fades out in 80 ms at the lift; after the drop, waits for a pointer move.
- **Fast scrubbing:** no holds; the latest item counts.
- **Only the pointer moves it:** an item that moves or resizes carries the pill on its drawn box, with no spring
  of its own. A layout change under a still pointer waits for the next move.
- An alert fill draws over the pill. It can overshoot ~1 dp: keep 2 dp at the strip's ends.

## 5. Reduced motion

At `ANIMATOR_DURATION_SCALE` 0 (which "Remove animations" sets), the popup is whole in its first frame and gone in
one frame, with no stagger. The pill cuts, appears and goes instantly. The 150 ms grace and the 350 ms guard stay,
because they're tolerances, not motion. Any other scale stretches every duration, delay, hold and spring alike.

## 6. Frame budget

- **Per frame, the popup changes only:** the glass height, one value driving the clip, outline, shadow and blur
  region (framed in a pre-draw listener, like Booklight's `frameGlass`); each block's `graphicsLayer`
  translationY and alpha, read in the layer lambda; presence. Without a `Window`, the fallback is the window's
  height following the glass, height only.
- **Never per frame:** window x, y or width; measuring the contents (no `animateContentSize`, no animated
  `Modifier.height`: clip in the draw phase); recomposition; text layout; a Sparkline's `Path` (remember it).
- **Strip:** one rounded rect in the Row's `drawBehind`. One frame loop (Booklight's `Band`) runs only while an
  edge moves, a hold counts or a fade runs; at rest nothing is scheduled. Hit-testing is a binary search over
  cached `events.placed` rects. The per-item hover backgrounds go away.
- **Check on the Lenovo:** no dropped frames while opening (release, 120 Hz); if the OS fades overlay windows in,
  drop our presence ramp.

**Prototypes:** exact springs on requestAnimationFrame, the real cubic-beziers, holds in frames at the measured
refresh rate; 0.25× stretches all of it. Not modelled: the cropped shadow, Esc, dragging.

## Open questions for Alex

1. **With a popup open, should the pill follow the pointer over other items?** Recommendation: no. It stays on
   the open item, which shows what is open, and it doesn't flick sideways as the pointer heads into the popup.
2. **Contents: drop into place, or rise like Booklight's rows?** Recommendation: drop, so they move with the
   glass. The prototype has both.

## 7. Version 2 (8 October): faster, and the contents' choreography

Alex asked for quicker motion, a choreography for what's inside the popups, and a "no animations" setting.
Everything gets about a fifth quicker, and each kind of content gets a small entrance, all in one order from the
top left. These numbers replace §1–4's.

### 7.1 Faster

| Part | v1 | v2 | Why |
| --- | --- | --- | --- |
| Opening T | 240 + 20 per 100 dp over 200, ≤ 340 | **160 + 0.16 × H, 190 to 270** (300 dp: 208; 616: 259) | 20 % shorter |
| Curve | (0.55, 0, 0.1, 1) | **(0.45, 0, 0.1, 1)** | still slow-fast-slow with a shorter wait: half open at 69 ms, not 96 (300 dp). A gentler middle, so the fastest step grows only 11 % (616 dp: 67 dp a frame, was 61). The rejected curve went the other way (0.7) |
| Presence | 100 ms | **80 ms** | |
| Block starts | 30 + i × min(20, 160 ÷ (n − 1)) | **24 + i × min(16, 128 ÷ (n − 1))** | the last block starts by 152 ms |
| Block drop | 8 dp, spring(0.86, 520) | **6 dp, spring(0.86, 700)** | readable at 57 ms (was 67); 0.03 dp past |
| Closing | contents 50; presence out 60 ms from fold end − 30 | **contents 40; presence out 50 ms from fold end − 25**; fold still 0.45 T | 300 dp: 119 ms (was 147) |
| Switching | dissolves in 90 ms | **75 ms** | |
| Strip pill | fades 120 ms | **fades 100 ms**; band unchanged | the approved rubber already answers in the first frame |
| Strip items appearing | 220 ms | **180 ms**, (0.2, 0, 0, 1) | |

Unchanged: the turn-round spring, the grace, the guard, the wait for the first frames.

### 7.2 The choreography

Each moving element is addressed by role and index (`Modifier.entrance(role, i)`); its progress is a function of
the popup's one clock. Blocks (§2) still drop in and carry their parts, which arrive in reading order: rows top to
bottom, each row left to right.

**Verbs.** Only layer properties and draw-phase effects:

| Verb | What | Timing |
| --- | --- | --- |
| drop | the whole block, 6 dp from above | spring(0.86, 700); a block without parts takes alpha smoothstep(0, 0.6) of it |
| fade | alpha | 100 ms, (0.2, 0, 0, 1) |
| pop | scale from s₀ about its centre, plus alpha | spring(0.9, 900): 0.15 % past, nothing shrinks back; alpha smoothstep(0, 0.5) |
| draw | a line trimmed left to right; the fill under it clipped to the line's head | 180 ms, (0.35, 0, 0.1, 1) |
| fill | a bar growing from its start to its value | 180 ms, same curve |

**Roles.** Offsets are counted from the block's start; j is the index in reading order.

| Role: parts | Verbs | Offsets, ms |
| --- | --- | --- |
| Header: icon tile, title, subtitle, trailing | pop 0.85, fade, fade, fade | 0, 20, 36, 48 |
| Big value ("70°", a countdown), its lines | pop 0.94 from its baseline's left, fade | 0, 24 + 16 j |
| InfoRow: label, value | fade, fade | 0, 24 |
| SectionLabel | drop only | 0 |
| MenuEntry: icon, label, detail or trailing | pop 0.8, fade, fade | 0, 20, 36 |
| Sparkline: track, line, second series | fade, draw, draw | 0, 30, 70 |
| HourStrip cell (time, glyph, temperature together) | pop 0.9 | 16 j |
| DayRow: day, glyph, chance, high, low | fade, pop 0.8, fade, fade, fade | 0, 12, 24, 36, 48 |
| TileGrid tile, row r, column c | pop 0.9 | 18 (r + c), a diagonal from the top left |
| ChipRow chip | pop 0.92 | 16 min(j, 6) |
| Meter; CoreBars bar | fill | 30; 30 + 12 j |
| Month grid day, week w, weekday d | pop 0.9 | 10 (w + d), a diagonal; today's ring pops from 0.6, 40 ms after its day |
| Media: artwork, title, artist, button j, progress | pop 0.92, fade, fade, pop 0.8, fill | 0, 24, 40, 56 + 16 j, 40 |
| Flight route | the flown part draws behind the plane, which rides the line's head; the rest of the route fades | 30; 160 + 160 × the share of the route flown, ms, on the glass's curve (Booklight's `flies`, quicker) |

**Never moves:** text itself (no counting or rolling), hover fills, a value updating live.

**One cap.** An element starts at its block's start plus its offset. If a popup's last start would come after
180 ms, every start is scaled by 180 ÷ that start, keeping order and proportions; durations never scale. So
everything has started by 180 ms, is readable by about 240 and at rest by about 360 (v1's Weather: 352, with far
less moving). Parts below a scrolling popup's visible height are at rest from the start.

**Closing has no choreography:** everything fades together in 40 ms; exits shouldn't perform.

**For the visual designer**, who owns how each role looks as it arrives: the roles, order and timing are fixed;
only layer and draw-phase changes, nothing past its place.

### 7.3 "No animations"

A switch in Look, "Animations", on by default; off is Alex's "no animations". Animations are off when the switch
is off or the system's Remove animations is on; otherwise the system's animator scale still applies.

**Off stops:** the popup's opening, choreography, fold, dissolve and turn-round (whole in its first frame, gone in
the next); the highlight's glide and fades (it jumps); strip items appearing, sliding aside, lifting to 1.08 and
settling after a drag; ‹ revealing hidden items; any fade of a changing value.

**Off keeps:** the 150 ms grace, the 350 ms guard and the 600 ms tooltip and collapse delays (tolerances, not
motion); a dragged item following the pointer one to one; the glass, blur and shadow; the wait for the first
frame, so the popup shows whole as soon as it's drawn.
