# BentoBar 1.3: glass and motion — the brief

1.3 is a visual and motion release: no new features.

## The request (Alex, 2026-10-08, word for word)

> Ok a few things. First, can we fix some visuals for the popups. I don't like the shadow that is below the popyps
> being sort of off to the left. Doesn't look great. Can we try the blur that booklight is using with the cropped
> shadow but less shadow since the boxes are smaller. And then can we make it so that the bubbles come out with
> beautiful animations expanding down from the top and the items inside sort of moving into place and when I scrubb
> across the icons in the status bar for bentobar the highlight comes with my mouse bubbling from one to another
> (similar to rows in the booklight). Plan this out and implement 1.3 with these motion and design improvements.
> This is a visual and motion design release. Have visual and motion designers do work here before implementing
> these fixes.

So, four things:
1. **Popup shadow:** centred and cropped (never bleeding under the glass), lighter than Booklight's.
2. **Popup glass:** a background blur like Booklight's panel.
3. **Popup motion:** the popup expands down from the top, and its contents move into place.
4. **Strip highlight:** one highlight follows the pointer across the strip's icons, gliding from one to the next
   like Booklight's rubber-band row highlight.

His taste, from earlier work: simple, quiet, nothing cluttered or floating; no hover surprises (hover highlights and
tooltips are fine, things that expand on hover are not); he approved Booklight's motion ("slow beginning then fast
then slow again") and asked there that "the motion designer really approves of any motion". He approves the design
before any code.

## BentoBar today (code facts, file:line under app/src/main/java/io/github/kuscher/bentobar/)

**Popups.**
- Each one is a new `TYPE_ACCESSIBILITY_OVERLAY` window, created on every open with `WindowManager.addView`
  (`bar/BarService.kt:749-778`, `bar/Overlay.kt:53-67`). Flags: `PixelFormat.TRANSLUCENT`, height `WRAP_CONTENT`,
  width exact, focusable, `FLAG_WATCH_OUTSIDE_TOUCH`.
- **Position:** the card's top sits 4 dp under the status bar. A popup under an item in the right half of the
  screen lines up its right edge with the item; in the left half, its left edge. The strip sits at the right by
  default.
- **Closing:** a touch outside, Esc, clicking the item again, or an entry's action. `removeViewImmediate` is
  instant, with no exit animation.
- **Root** (`bar/Menus.kt:46-69`), shared by every popup:
  - `Box(padding 12 dp)` › `Surface(RoundedCornerShape(20.dp), colorScheme.surfaceContainer, shadowElevation = 8.dp)`
    › a scrolling `Column` (max height = screen minus bar minus 170 dp).
  - The only entrance today: 140 ms, alpha 0→1 and translateY −8→0 dp.
- **Widths:** 300 dp by default; 340 for Flight, Calendar, Event, Weather and Folder; 320 Media; 380 Timer; 392 Tools;
  280 the right-click menu; 290 the ‹ menu.
- **Colours:** Material dynamic colour (`ui/Theme.kt`), light or dark with the system.
- **Contents** are built from `ui/MenuKit.kt` (`MenuCard` header + content with 14×12 dp padding, `MenuEntry` 40 dp
  rows with 12 dp corners, `InfoRow`, `SectionLabel`, `Sparkline`, `TileGrid`, `ChipRow`, `Meter`, …).
  - 19 item types have a popup, plus the right-click menu and the ‹ menu.
  - One shared place wraps them all: `MenuSurface`'s Column.

**Why the shadow looks off to one side.** `shadowElevation = 8.dp` is an elevation shadow. Android's light sits at
the display's horizontal centre, about 600 dp up, so a shadow is pushed sideways *away from the screen's centre*
(roughly the elevation × distance from centre ÷ 600). The window gives it only 12 dp of room, so on the far side it
is cut off hard at the window edge, and on the near side it almost disappears. A popup left of centre leans left; one
right of centre leans right.

**The strip.**
- **Layout:** a `Row` holding the ‹ chevron and `FitRow`, a custom Layout with 12 dp between items
  (`bar/BarUi.kt:199-257`). Its height is the status bar's (36 dp on the Lenovo at density 1.5; 2880×1800 px =
  1920×1200 dp), minus 3 dp padding top and bottom.
- **Pill options:** none, the text colour at 12%, or solid #E6202124 / #E6F1F3F4, clipped to a full round.
- **Hover today:** each item draws its *own* box: 10 dp corners, the text colour at 14%, no animation, covering the
  item plus 6 dp at each end (`BarUi.kt:346-350, 431-440`). There's no shared highlight, the pointer's x position is
  never tracked, and the box drops out in the 12 dp gaps.
- **Rectangles:** each item's is known (`events.placed`).
- **Other hover behaviour:**
  - a tooltip after 600 ms of hover;
  - optional hover-to-reveal of hidden items (off by default);
  - while an item's popup is open its box stays on (`held`);
  - items lift to 1.08 while dragged to reorder.
- **Motion and testing in the code:** Compose follows the system's animator duration scale. There are no shape
  tokens. Pure, unit-tested rules are the house pattern (`items/TickRules.kt`, `bar/ColorWatch.kt`).

## Booklight, the reference (~/booklight, read-only)

Its panel is an **Activity window** (translucent, its own task), which matters: BentoBar's popups are overlay views.

**Blur.**
- `Window.setBackgroundBlurRadius`; the corner radius is read from the background drawable's outline.
- Levels Clear / Balanced (default) / Frosted: veil alpha 0.28 / 0.42 / 0.58 light and 0.38 / 0.50 / 0.64 dark, over
  `surfaceContainerLowest`; blur 14 / 22 / 32 dp.
- When the platform has no cross-window blur (battery saver, a developer option, a weak GPU), the veil goes opaque
  (`surfaceContainerHigh`).
- "The blur follows the glass": the blurred region is the root view's rectangle. A pre-draw listener reshapes it to
  the growing glass every frame and moves the children back. The radius is the full radius × the panel's presence
  from the first frame, and never 0 on the way in (at 0 the platform drops the blur layer).

**Shadow.**
- The system's *window* shadow, `window.setElevation`, with a custom outline. Levels as elevation / spot alpha /
  ambient alpha:
  - Low: 72 dp / 0.14 / 0.03
  - Medium: 96 dp / 0.24 / 0.05
  - High: 128 dp / 0.36 / 0.07
  - Dark theme: × 1.4
- **Cropped:** the background drawable clears the glass's shape (PorterDuff CLEAR), so no shadow shows through the
  translucent glass.
- The light above makes the lower edge darkest. Measured at 72 dp: 15% at the lower edge, gone 30 dp out; the sides
  about half that; the top 2–3%.
- A drawn variant exists for the in-app preview: a BlurMaskFilter round rect with radius = elevation × 0.4, offset
  down elevation × 0.22, alpha spot + ambient.

**Glass look.**
- Panel radius 32 dp.
- A white 1.25 dp outline at 0.80 light / 0.44 dark, plus a black hairline in the outermost px at 0.20 / 0.28.

**Opening** (times at the default Medium):
- A 6 dp seam grows for 160 ms on cubic-bezier(0.2, 0, 0, 1), then waits 60 ms.
- The glass then widens from the centre seam over 360 ms on cubic-bezier(0.55, 0, 0.1, 1) ("slow-fast-slow", no
  overshoot). The window never resizes; the glass is uncovered, not scaled.
- Contents: alpha = smoothstep(0.35, 0.85) of how open the glass is.
- Rows: 22 ms stagger, each rises 12 dp with alpha on spring(damping 0.86, stiffness 520).
- **Rejected in Booklight's reviews:** cubic-bezier(0.7, 0, 0.1, 1) ("jumps in the middle"), and a spring that
  overshot and shrank back.

**Closing:**
- The glass folds in 105 ms on cubic-bezier(0.45, 0, 0.4, 1), the last 40 ms fading out; 155 ms in all.
- A re-press while closing reopens on spring(1.0, 1000) from the current speed.

**Reduced motion:** with `ANIMATOR_DURATION_SCALE` = 0 everything snaps.

**The rubber-band row highlight** (variant A, approved: "I agree with A for the rubber band"):
- The leading edge moves at once on spring(0.85, 1400).
- The old edge holds 5 frames (40 ms), then follows on spring(0.86, 900).
- Long moves (more than 98 dp): the old edge holds 2 frames, then follows on spring(0.90, 1400).
- The stretch shows as is up to 40 dp past the row, then is eased so it never exceeds 56 dp.
- A pill that is already moving doesn't hold; it continues from where it is at its current speed.
- Hover selects only while the pointer moves.
- **Look:** radius 24 dp; white 1 px border at 0.55 light / 0.30 dark; fill `secondaryContainer` at 0.78 light,
  0.42 dark on glass. Colour cross-fades in 120 ms; the pill fades in and out in 120 ms.
- **Measured:** a one-row step stretches 34 dp and is at rest after 197 ms.

**Other device facts:**
- Googlebook OS fades a translucent *task* in and out over about 140–200 ms (Activities only; overlay windows
  probably skip it, unmeasured).
- 8.3 ms frames (120 Hz) while animating; the display drops to 60 Hz when idle.
- Resizing a window's *width* every frame breaks; height-only changes are fine.
- Anything moving past the window's edge is clipped, so overshoot must bounce inwards.

## Constraints for 1.3
- Popups stay overlay windows above everything (the accessibility service draws them; they appear under the status
  bar, near a screen edge most of the time). An overlay view has no `Window`. A popup hosted in a `Dialog` whose
  window type is `TYPE_ACCESSIBILITY_OVERLAY` would have one (to be proven).
- Blur can be unavailable at any time (battery saver): the solid fallback must look as finished as the glass.
- No new permissions. Nothing reads other apps' windows: the blur is the compositor's, not a screenshot.
- The strip is always on screen: its highlight animates only while the pointer moves over it, and costs nothing
  otherwise.
- Popups are small (280–392 dp wide, often 150–500 dp tall): the shadow must be lighter than Booklight's panel's.
