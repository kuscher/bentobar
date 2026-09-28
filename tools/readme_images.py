#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Composes the README images from raw captures of a Googlebook.

Raw captures (in RAW, default ~/.cache/discobar/shots), made with a demo layout so no personal data
shows:
  bar_hero.png, bar_now.png, bar_open.png   full-width screenshots, top 41 px (./disco shot F 41)
  win_*.png                                 DiscoBar's own windows (./disco debug winshot ...), which
                                            carry their shadow margin and no mouse pointer
  app_*.png                                 the settings window (./disco debug winshot app)
  cpu_frame.txt                             "cpu menu frame: x1 y1 x2 y2" of the CPU menu window

Writes docs/images/hero.png, bar.png, menus.png, settings.png and settings-pages.png.

    python3 tools/readme_images.py [RAW]
"""
import pathlib
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = pathlib.Path(__file__).resolve().parent.parent
RAW = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else pathlib.Path.home() / ".cache/discobar/shots")
OUT = ROOT / "docs/images"


def rounded(im, r):
    im = im.convert("RGBA")
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, im.width - 1, im.height - 1), r, fill=255)
    im.putalpha(ImageChops.multiply(im.getchannel("A"), mask))
    return im


def shadowed(im, blur=18, offset=(0, 8), alpha=90, pad=40):
    """[im] on a transparent canvas with a soft drop shadow."""
    w, h = im.size
    canvas = Image.new("RGBA", (w + 2 * pad, h + 2 * pad), (0, 0, 0, 0))
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    a = im.getchannel("A").point(lambda v: v * alpha // 255)
    shadow.paste(Image.new("RGBA", im.size, (20, 20, 40, 255)), (pad + offset[0], pad + offset[1]), a)
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(blur)))
    canvas.alpha_composite(im, (pad, pad))
    return canvas


def card(win):
    """A menu window capture reduced to its card (the window keeps a 12 dp margin around it)."""
    bbox = win.getchannel("A").point(lambda v: 255 if v > 200 else 0).getbbox()
    return rounded(win.crop(bbox), 22)


def wallpaper(bar, height):
    """Continues the status bar's wallpaper below it: per-column colours without the glyphs, smoothed."""
    px = bar.convert("RGB").load()
    cols = []
    for x in range(bar.width):
        vals = [px[x, y] for y in range(bar.height) if sum(px[x, y]) < 600]  # skip white glyph pixels
        cols.append(tuple(sum(c[i] for c in vals) // len(vals) for i in range(3)) if vals else cols[-1] if cols else (90, 120, 140))
    k = 60
    smooth = [tuple(sum(cols[j][i] for j in range(max(0, x - k), min(len(cols), x + k))) // (min(len(cols), x + k) - max(0, x - k))
                    for i in range(3)) for x in range(len(cols))]
    bg = Image.new("RGB", (bar.width, height))
    d = bg.load()
    for y in range(height):
        f = 1 - 0.28 * y / height  # a little darker downwards
        for x in range(bar.width):
            r, g, b = smooth[x]
            d[x, y] = (int(r * f), int(g * f), int(b * f))
    return bg.filter(ImageFilter.GaussianBlur(6))


def main():
    OUT.mkdir(parents=True, exist_ok=True)

    # Hero: the right part of the status bar with the CPU menu open under its item.
    x0 = 820
    bar = Image.open(RAW / "bar_hero.png").convert("RGBA").crop((x0, 0, 1920, 41))
    frame = [int(v) for v in (RAW / "cpu_frame.txt").read_text().split(":")[1].split()]
    menu = Image.open(RAW / "win_cpu.png").convert("RGBA")
    h = frame[1] + menu.height + 36
    hero = wallpaper(bar, h).convert("RGBA")
    hero.alpha_composite(bar, (0, 0))
    hero.alpha_composite(menu, (frame[0] - x0, frame[1]))  # the window capture keeps its own shadow
    rounded(hero, 20).save(OUT / "hero.png", optimize=True)

    # The bar folded and with hidden items revealed.
    x0 = 560
    rows = [Image.open(RAW / n).convert("RGBA").crop((x0, 0, 1920, 41)) for n in ("bar_now.png", "bar_open.png")]
    gap = 10
    both = Image.new("RGBA", (rows[0].width, 41 * 2 + gap), (0, 0, 0, 0))
    both.alpha_composite(rounded(rows[0], 12), (0, 0))
    both.alpha_composite(rounded(rows[1], 12), (0, 41 + gap))
    both.save(OUT / "bar.png", optimize=True)

    # Menus side by side.
    cards = [card(Image.open(RAW / f"win_{n}.png").convert("RGBA")) for n in ("network", "timer", "ctx", "tools", "discobar")
             if (RAW / f"win_{n}.png").exists()]
    pad, gap = 30, 28
    W = sum(c.width for c in cards) + gap * (len(cards) - 1) + 2 * pad
    H = max(c.height for c in cards) + 2 * pad
    sheet = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    x = pad
    for c in cards:
        s = shadowed(c, blur=14, alpha=70, pad=pad)
        sheet.alpha_composite(s, (x - pad, 0))
        x += c.width + gap
    sheet.save(OUT / "menus.png", optimize=True)

    # Settings: the Bar page, and Add + Look side by side at half size.
    top = 44  # where the (separate) caption window sits
    main_page = rounded(Image.open(RAW / "app_bar.png").convert("RGBA").crop((0, top, 1382, 864)), 18)
    shadowed(main_page, blur=20, alpha=80, pad=36).save(OUT / "settings.png", optimize=True)
    pages = [rounded(Image.open(RAW / f"app_{n}.png").convert("RGBA").crop((0, top, 1382, 864)), 18) for n in ("add", "look")]
    half = [p.resize((p.width // 2, p.height // 2), Image.LANCZOS) for p in pages]
    combo = Image.new("RGBA", (half[0].width * 2 + 24 + 2 * 24, half[0].height + 2 * 24), (0, 0, 0, 0))
    combo.alpha_composite(shadowed(half[0], blur=12, alpha=70, pad=24), (0, 0))
    combo.alpha_composite(shadowed(half[1], blur=12, alpha=70, pad=24), (half[0].width + 24, 0))
    combo.save(OUT / "settings-pages.png", optimize=True)
    for f in sorted(OUT.glob("*.png")):
        print(f.relative_to(ROOT), Image.open(f).size, f.stat().st_size // 1024, "KB")


if __name__ == "__main__":
    main()
