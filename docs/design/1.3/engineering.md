# BentoBar 1.3: engineering notes (glass and motion)

## Why this exists in plain English
The designers are drawing glass popups that unfold from the bar, and one highlight that glides across the strip. This
note says how an accessibility service can build each piece on Android, what is already proven (by Android's source,
or by Booklight on the same Googlebooks) and what must be tried on the Lenovo first. Each section ends with a decision
and how sure I am. Line numbers are BentoBar 1.2, Booklight's public repo and AOSP `main`.

## 1. Background blur on the popups
- **Only a `Window` can blur its background**; the public way to get one is a `Dialog`. `Dialog.show()` adds its decor
  through the WindowManager of the context it was made with, and an AccessibilityService's WindowManager fills in the
  service's token for a window without a parent (`Dialog` constructor and `show()`,
  `AccessibilityService.getSystemService`, `WindowManagerImpl.applyTokens`). So `ComponentDialog(service, theme)` of
  type `TYPE_ACCESSIBILITY_OVERLAY` needs no token of ours, and brings the owners Compose needs. Theme:
  `windowIsTranslucent` (DecorView blurs only translucent windows, `updateBackgroundBlurRadius`), no dim, no window
  animation, minimum widths 0. Flags as today (`Overlay.kt:53-67`).
- (b) `FLAG_BLUR_BEHIND` blurs the whole screen: rejected. (c) Nothing else is public (`BackgroundBlurDrawable` and
  `ViewRootImpl.createBackgroundBlurDrawable` are internal).
- **The region** is the decor's own rectangle: DecorView puts a `BackgroundBlurDrawable` under the window's background
  (`updateBackgroundDrawable`), which reports its render node's position every frame (`mPositionUpdateListener`). It
  cannot be inset publicly. **Corners**: one radius, read from the background drawable's outline before each frame
  (`updateBackgroundBlurCorners`).
- **A blur smaller than the window** (room for a drawn shadow) is Booklight's framing: in a pre-draw listener,
  `decorView.setLeftTopRightBottom(card)`, children translated back (`OverlayActivity.kt:320-336`). Done every frame it
  follows the opening; Booklight measured it within 0.5 px at 120 Hz. The window never resizes.
- **Input under framing**: Android hands events to the root view without its offset (`ViewRootImpl`,
  `mView.dispatchPointerEvent`), but Compose maps raw screen coordinates (`MotionEventAdapter`: `getRawX` +
  `screenToLocal`, Compose UI 1.12.1). The card's touches and hover land right; the View-level hit test is shifted
  only into the right margin, which counts as outside anyway (section 3).
- **Fallback**: `isCrossWindowBlurEnabled`, then `addCrossWindowBlurEnabledListener` (answers at once,
  `CrossWindowBlurListeners.addListener`); off means the opaque veil.
- **Radius** at least 1 px before `show()`: at 0 DecorView drops the layer and rebuilds it a message later
  (`DecorView.setBackgroundBlurRadius`; Booklight `OverlayActivity.kt:288-292`).

**Decision:** `ComponentDialog` of type `TYPE_ACCESSIBILITY_OVERLAY`, `setBackgroundBlurRadius`, decor framed to the
visible card each frame. **Confidence:** high from the source; medium until P1–P2 show a blurred overlay.

## 2. The shadow
Android's light: x at the display's centre, y at its top, z = 500 dp × (shorter side / 450 dp + 2) / 3 = **778 dp**
on 1920 × 1200 dp (`ThreadedRenderer.setLightCenter`), radius 800 dp. Skia moves a spot shadow d·z/(lz−z) away from
the light and blurs it R·z/(lz−z) (`SkDrawShadowInfo.h`, `GetSpotParams`):

| Elevation, lz 778 / 600 dp | Lean 600 dp from centre | Lean 800 dp (edge) | Spot blur |
| --- | --- | --- | --- |
| 8 dp (today) | 6.2 / 8.1 dp | 8.3 / 10.8 dp | 8.3 / 10.8 dp |
| 72 dp (Booklight Low) | 61 / 82 dp | 82 / 109 dp | 82 / 109 dp |

Lean ÷ blur = d ÷ 800 dp: 600 dp out, the shadow is pushed three quarters of its own softness at any elevation, then
cut by the 12 dp window. An ambient-only system shadow is centred but even all round, and its top falls on the bar.

- (i) **`Modifier.dropShadow(shape, Shadow(radius, color, spread, offset, alpha))`** is in Compose UI 1.12.1 (BOM
  2026.09.00). Drawn, no light: a `BlurMaskFilter` into an ALPHA_8 bitmap of the card plus 2 × (radius + spread),
  cached per size in `AndroidShadowContext`, never evicted (javap). Fine at a fixed size; a growing card makes and
  keeps a bitmap per frame.
- (ii) **`BlurMaskFilter` round rect on the hardware canvas**, per frame (Booklight `Stage.kt:189-199`, on these
  devices). The hardware-acceleration docs still list `setMaskFilter` as unsupported: probe it.
- (iii) **The system window shadow** leans as above.

Recommended: drawn, centred, offset only downwards, at the visible card's rectangle every frame (it grows with the
reveal; alpha × presence), cropped with `clipPath(card, ClipOp.Difference)`: nothing under the glass, no offscreen
layer (Booklight clears because the system shadow is drawn before the window). Margins: sides ≥ blur + spread (about
16 dp), bottom ≥ blur + offset, top 0.

**Decision:** (ii); a nine-slice of one bitmap blurred per popup if P5 shows it missing or over 1 ms a frame.
`dropShadow` only for shapes that never resize. **Confidence:** high on centring; medium on (ii)'s cost.

## 3. Window and reveal
- **Size**: exact width (card + margins), height `WRAP_CONTENT` from the full content in the first traversal (the
  580 dp cap is width-only). Nothing resizes during the reveal; later content changes resize the height once, safely.
- **Position**: window top = card top = bar bottom + 4 dp. 1.2's margin reaches 8 dp into the bar and eats clicks
  there (`BarService.kt:773`).
- **Reveal**, draw phase only, from one value (card pixels shown, from `MenuMotion`): the card's `graphicsLayer` clip,
  the shadow, the decor framing, blur = full × presence. Rows: `MenuCard`'s column becomes a `Layout` placing each
  child `placeWithLayer { alpha; translationY }` by its top against the revealing edge, covering all 32 `MenuCard`
  uses. No window alpha: every change is a relayout, and view alpha never reaches the blur (the radius does).
- **Focus, Esc, outside**: same flags; `setCancelable(false)`; the dialog's `dispatchKeyEvent` closes on Esc,
  `dispatchTouchEvent` on `ACTION_OUTSIDE` or a press outside the card.
- **Closing**: `closeMenu()` clears `menu`, `menuKey` and the ticker at once, makes the window untouchable and
  unfocusable (one relayout), plays the exit, dismisses. A main-thread timer removes it at exit + 250 ms regardless, so
  a stalled frame clock (screen off) leaves nothing. `stop()`, `onConfigChanged()` and a hidden strip remove both
  windows at once (`BarService.kt:234-250, 272-275`). Animator scale 0: no exit.
- The ‹ menu and the right-click menu already go through `toggleMenu` (`BarService.kt:749`) and share it all.

**Decision:** one `MenuWindow` host as above. **Confidence:** high.

## 4. The strip's one highlight
- **Pointer**: the Row's `pointerInput` (`BarUi.kt:224-237`) already sees Enter, Exit and Move; read x on
  `PointerEventPass.Initial`, never consume, so clicks, sliders and dragging are untouched.
- **Hover on the strip window**: 1.2's tooltip relies on it (`BarUi.kt:350` → `BarService.kt:697`), but no device
  check is recorded (device-findings: "not tested yet"). P7.
- **Targeting**: items report their rectangles (`placed`), ‹ included. Pure `StripHighlight.target(x, spans, current,
  gapRule)`; a pinned item wins (a held slider, `BarUi.kt:392, 437`). Correction to the brief: nothing keeps a box on
  for an open popup (`held` is the slider's press); lighting it is the motion designer's call. Hidden while dragging.
- **Motion**: Booklight's `Band` (`Rows.kt:350-458`, `Motion.kt:63, 218-224`) along x: frame-counted hold, edges
  keep their speed, stretch knee and limit. `withFrameNanos` only while moving, read only in the Row's `drawBehind`.
- **Remove** the per-item boxes (`BarUi.kt:432-437`, chevron `323-325`); keep `hoverable` for tooltip and slider handle.
- **Cost**: idle, nothing runs; hovering, one lookup per Move; frames only while the band moves.

**Decision:** as above. **Confidence:** high in code; medium until P7.

## 5. Architecture and tests

| File | Responsibility | Tested |
| --- | --- | --- |
| `ui/MenuMotion.kt` | Curves, shown height, blur and shadow presence, row entrance by y, close, reduced motion | JVM |
| `bar/StripHighlight.kt` | Target and gap rule; the band stepped by frame times | JVM |
| `bar/MenuWindow.kt` | Dialog host: type, theme, flags, framing, blur listener, Esc, outside, exit, removal timer | device |
| `ui/Glass.kt` | Outline-only background drawable; `Modifier.menuGlass` (veil, hairline, fallback), `menuShadow` | device |
| `Menus.kt`, `MenuKit.kt`, `BarUi.kt`, `BarService.kt` | Use them; `Overlay` stays for strip and tooltip | device |

JVM tests in the `TickRules` style: curves monotonic and ending exactly; blur never 0 while opening; scale 0 snaps;
targets across every gap; pinned wins; stretch within its limit; holds in frames; a turn keeps speed.

**Decision:** two pure models, one window host. **Confidence:** high.

## 6. Device probe plan
**`applicationIdSuffix ".dev"` on the debug build**, not uninstalling 1.2: a release build has no `debug cfg`, so
uninstalling loses Alex's layout. What the suffix touches:
- A second accessibility entry: `./bento enable` switches it on over adb, Alex clicks its disclosure once, and 1.2's
  service is off during probes (two strips would share the free area).
- `./bento`: `SVC` and `SVC_SHORT` (`bento:19-20, 53`) build the class name from `PKG`, wrong once `PKG` gains
  `.dev`; the script sends `$PKG.DEBUG` but the receiver's action is fixed (`src/debug/AndroidManifest.xml:14`).
- Separate data (load the demo layout); duplicate tiles and launcher entries (label them "dev"). `<queries>` and the
  chip's explicit intents are unaffected.

Smallest first; debug timings are an upper bound (release is 3–4× faster):

| # | Probe | Measure |
| --- | --- | --- |
| P1 | Dialog overlay from the service | `dumpsys window` type 2032; focus, Esc, outside; no system animation |
| P2 | Blur on it | full `screencap` over a pattern (window shots omit blur); battery saver flips the listener |
| P3 | Framed in a larger window | blur only under the card, its corners; clicks at the right edge and last row |
| P4 | Blur follows the reveal | `screenrecord`, real speed and 4× slow: blur edge within 0.5 px of the glass |
| P5 | Drawn shadow | renders; centred at both edges; identical pixels under the glass with it on and off |
| P6 | Exit and leaks | close every way mid-animation, service off mid-exit: no "BentoBar menu" in `dumpsys window` |
| P7 | Strip hover | Move x in the log; no frames idle (`gfxinfo` over 10 s); CPU over 60 s |
| P8 | Frame times | `gfxinfo reset`, ten opens and closes, `framestats`: none over 8.3 ms |

**Decision:** the suffix; P1 to P8 in order. **Confidence:** high.

## Risks

| Risk | Effect | Mitigation |
| --- | --- | --- |
| No blur on an accessibility overlay layer | No glass | P1–P2 first; the opaque fallback is finished |
| Dialog window differs from `Overlay` (focus after the shortcut, insets) | Esc or layout regressions | P1; theme without insets |
| `BlurMaskFilter` missing or slow in hardware | No or janky shadow | Nine-slice |
| A later Compose maps input differently | Mis-hits on the card | P3; offset events in `dispatchTouchEvent` |
| Exit never ends | A window left up, which Play Protect watches for | Timer removal; instant close on stop |
| Hover never reaches the strip | No gliding highlight | P7 before strip work |
