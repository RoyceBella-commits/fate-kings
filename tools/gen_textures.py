#!/usr/bin/env python3
"""Draws every texture of the mod procedurally (Pillow).

Usage: python3 tools/gen_textures.py [preview_dir]

Nothing here traces official artwork. Vanilla armour / skin / horse-armour textures are read from
the local Loom cache only as alpha masks, i.e. to know which UV cells a model actually shows.
Colours follow the palette in the design document (chapter 10.3 for Excalibur).
"""
import io
import json
import math
import os
import pathlib
import random
import sys
import zipfile

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent
TEX = ROOT / "src" / "main" / "resources" / "assets" / "fatekings" / "textures"
CLIENT_JAR = pathlib.Path(os.path.expanduser("~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar"))


def C(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


CLEAR = (0, 0, 0, 0)

# --- Excalibur palette (design doc 10.3) ---
BLADE_HI = C("F4F7FF")
BLADE = C("C7D1E3")
BLADE_DK = C("8896B4")
RUNE = C("E8C766")
GUARD = C("EBB935")
GUARD_DK = C("A5760F")
KNIGHT_BLUE = C("2C57C8")
KNIGHT_BLUE_DK = C("1A367E")
NAVY_LINE = C("1B1F3B")
LIGHT_CORE = C("FFF7DA")
LIGHT_EDGE = C("FFE38A")
WIND = C("DDF4FF")
WIND_DK = C("9FD3EF")

# --- King of Heroes palette ---
GOLD_HI = C("FFF1A8")
GOLD = C("F2C230")
GOLD_MID = C("D99E1E")
GOLD_DK = C("9C6512")
GOLD_LINE = C("4A2A06")
RED = C("C8141E")
RED_DK = C("7A0A12")
RED_HI = C("FF5A4A")
EMERALD = C("1FB873")
EMERALD_DK = C("0C7446")
EMERALD_HI = C("7CF0B4")

# --- King of Knights palette ---
SILVER_HI = C("F2F5FB")
SILVER = C("C9D0DE")
SILVER_DK = C("8C95AA")
DRESS = C("2849A8")
DRESS_HI = C("3E66C9")
DRESS_DK = C("172C6B")
HAIR = C("F2D46A")
HAIR_HI = C("FFEBA0")
HAIR_DK = C("C79E34")
SKIN = C("F3D2BE")
SKIN_DK = C("DDB39C")


def new(w, h, fill=CLEAR):
    return Image.new("RGBA", (w, h), fill)


def put(img, x, y, c):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), c)


def rect(img, x0, y0, x1, y1, c):
    """Fills [x0, x1) x [y0, y1)."""
    for y in range(y0, y1):
        for x in range(x0, x1):
            put(img, x, y, c)


def opaque(img, x, y):
    return 0 <= x < img.width and 0 <= y < img.height and img.getpixel((x, y))[3] >= 100


def outline(img, color, diagonal=False, only_opaque=True):
    """Draws a 1-px outline around the opaque shape (4- or 8-neighbourhood)."""
    src = img.copy()
    n = [(1, 0), (-1, 0), (0, 1), (0, -1)]
    if diagonal:
        n += [(1, 1), (1, -1), (-1, 1), (-1, -1)]
    for y in range(img.height):
        for x in range(img.width):
            if src.getpixel((x, y))[3] > 0:
                continue
            if any(opaque(src, x + dx, y + dy) for dx, dy in n):
                img.putpixel((x, y), color)
    return img


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


def shade(c, f):
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))), max(0, min(255, int(c[2] * f))), c[3])


def with_alpha(c, a):
    return (c[0], c[1], c[2], a)


def save(img, rel):
    p = TEX / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    img.save(p)
    return img


def save_anim(frames, rel, frametime, interpolate=False):
    w, h = frames[0].size
    strip = new(w, h * len(frames))
    for i, f in enumerate(frames):
        strip.paste(f, (0, i * h))
    save(strip, rel)
    meta = {"animation": {"frametime": frametime}}
    if interpolate:
        meta["animation"]["interpolate"] = True
    (TEX / (rel + ".mcmeta")).write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8")
    return frames[0]


def diag(s, d):
    """Cell on the 45 degree sword lattice: s = x + y (across), d = x - y (along the blade)."""
    return ((s + d) // 2, (s - d) // 2)


# ---------------------------------------------------------------------------------------------
# Excalibur (three states)
# ---------------------------------------------------------------------------------------------

def excalibur_hilt(img, bright=False):
    # Crossguard: perpendicular staircase centred on the blade (s = 15.5), gold with a blue heart.
    for d, ss in ((-4, range(12, 21, 2)), (-5, range(11, 20, 2))):
        for s in ss:
            dist = abs(s - 15.5)
            if dist < 1:
                c = KNIGHT_BLUE
            elif dist < 2:
                c = GUARD if bright else GUARD
            elif dist < 4:
                c = GUARD
            else:
                c = GUARD_DK
            if bright and c != KNIGHT_BLUE:
                c = mix(c, LIGHT_CORE, 0.35)
            put(img, *diag(s, d), c)
    # Top light on the guard's upper row.
    for s in range(13, 19, 2):
        put(img, *diag(s, -5), mix(GUARD, C("FFF3B0"), 0.45 if not bright else 0.7))
    # Blue grip with a darker wrap.
    grip = [(16, -6, KNIGHT_BLUE), (15, -7, KNIGHT_BLUE_DK), (16, -8, KNIGHT_BLUE_DK), (15, -9, KNIGHT_BLUE)]
    for s, d, c in grip:
        put(img, *diag(s, d), c)
    # Gold pommel.
    put(img, *diag(16, -10), GUARD)
    put(img, *diag(15, -11), GUARD_DK)
    put(img, *diag(14, -10), GUARD)


def blade_cells():
    axis = [diag(15, d) for d in range(-3, 14, 2)]   # (6,9) .. (14,1)
    second = [diag(16, d) for d in range(-2, 13, 2)]  # (7,9) .. (14,2)
    return axis, second


def excalibur_revealed():
    img = new(16, 16)
    axis, second = blade_cells()
    for i, p in enumerate(axis):
        put(img, *p, BLADE_HI)
    for i, p in enumerate(second):
        put(img, *p, BLADE_DK if i < 1 else BLADE)
    # Fairy script: two pale-gold dots in the third of the blade next to the guard.
    put(img, *diag(16, 0), RUNE)
    put(img, *diag(16, 4), RUNE)
    excalibur_hilt(img)
    outline(img, NAVY_LINE)
    return img


def excalibur_air(frames=8):
    out = []
    axis, _ = blade_cells()
    for f in range(frames):
        img = new(16, 16)
        excalibur_hilt(img)
        outline(img, NAVY_LINE)
        # A faint shimmer traces the blade so the slot never looks empty.
        for i, p in enumerate(axis):
            if img.getpixel(p)[3] == 0:
                put(img, *p, with_alpha(WIND, 55 if (i + f) % 3 else 95))
        # Two or three translucent wind arcs slide from the guard towards the tip.
        for k in range(3):
            pos = (k * 3.0 + f * 3.0 / frames) % 9.0
            i = int(pos)
            fade = 1.0 - max(0.0, (pos - 6.0) / 3.0)
            x, y = axis[i]
            cells = ((x - 1, y - 1, WIND_DK, 150), (x, y, WIND, 215), (x + 1, y, WIND, 200), (x + 1, y + 1, WIND_DK, 150),
                     (x - 1, y, WIND_DK, 90), (x + 2, y + 1, WIND_DK, 80))
            for cx, cy, c, a in cells:
                if 0 <= cx < 16 and 0 <= cy < 16 and img.getpixel((cx, cy))[3] < 120:
                    put(img, cx, cy, with_alpha(c, int(a * fade)))
        out.append(img)
    return out


def excalibur_release(frames=8):
    out = []
    axis, second = blade_cells()
    for f in range(frames):
        img = new(16, 16)
        # Soft halo around the whole blade instead of a dark outline.
        for p in axis + second:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1)):
                q = (p[0] + dx, p[1] + dy)
                if 0 <= q[0] < 16 and 0 <= q[1] < 16:
                    put(img, *q, with_alpha(LIGHT_EDGE, 110))
        for i, p in enumerate(axis):
            put(img, *p, LIGHT_CORE)
        for i, p in enumerate(second):
            put(img, *p, LIGHT_EDGE)
        # Light flowing from the guard to the tip.
        head = (f * 12.0 / frames)
        for i, p in enumerate(axis):
            dist = abs(i - head)
            if dist < 1.5:
                put(img, *p, C("FFFFFF"))
                if i < len(second):
                    put(img, *second[i], C("FFF9E6"))
        put(img, *diag(16, 0), C("FFD54A"))
        put(img, *diag(16, 4), C("FFD54A"))
        excalibur_hilt(img, bright=True)
        # Outline only where the halo left gaps (keeps the hilt readable).
        base = img.copy()
        for y in range(16):
            for x in range(16):
                if base.getpixel((x, y))[3] == 0 and any(
                        opaque(base, x + dx, y + dy) and base.getpixel((x + dx, y + dy))[0] < 200
                        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    put(img, x, y, with_alpha(NAVY_LINE, 200))
        out.append(img)
    return out


# ---------------------------------------------------------------------------------------------
# King of Heroes items
# ---------------------------------------------------------------------------------------------

def gate_of_babylon():
    img = new(16, 16)
    cx, cy = 7.5, 7.5
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - cx, y - cy)
            if r > 7.3:
                continue
            ring = r % 2.4
            if r > 6.3:
                c = GOLD_DK
            elif ring < 1.0:
                c = GOLD_HI if (x + y) % 5 else C("FFFFFF")
            else:
                c = GOLD if r < 4.5 else GOLD_MID
            put(img, x, y, c)
    # A hilt peeking out of the centre ripple, with a red gem.
    for (x, y, c) in ((7, 5, GOLD_DK), (8, 5, GOLD_DK), (7, 6, RED), (8, 6, RED_DK), (7, 7, GOLD_LINE), (8, 7, GOLD_LINE),
                      (6, 7, GOLD_DK), (9, 7, GOLD_DK), (7, 8, C("E8EEF8")), (8, 8, C("B9C4D6")), (7, 9, C("E8EEF8")), (8, 9, C("B9C4D6"))):
        put(img, x, y, c)
    outline(img, GOLD_LINE)
    return img


def bab_ilu():
    img = new(16, 16)
    # Key blade along the sword lattice, with teeth that stick out.
    for d in range(-3, 12, 2):
        put(img, *diag(15, d), GOLD_HI)
    for d in range(-2, 11, 2):
        put(img, *diag(16, d), GOLD_MID)
    for d in (4, 8):  # teeth
        put(img, *diag(17, d - 1), GOLD_MID)
        put(img, *diag(18, d), GOLD_DK)
    put(img, *diag(15, 11), GOLD_HI)
    # Key bow: a small ring at the hilt with a red gem.
    ring = [(2, 10), (3, 10), (4, 10), (1, 11), (5, 11), (1, 12), (5, 12), (1, 13), (5, 13), (2, 14), (3, 14), (4, 14)]
    for p in ring:
        put(img, *p, GOLD)
    for p in ((2, 11), (3, 11), (4, 11)):
        put(img, *p, GOLD_HI)
    put(img, 3, 12, RED)
    put(img, 3, 13, RED_DK)
    put(img, 5, 9, GOLD_DK)
    put(img, 6, 10, GOLD_DK)
    outline(img, GOLD_LINE)
    return img


def ea_frames(frames=4, fast=False):
    out = []
    for f in range(frames):
        img = new(16, 16)
        # Three stacked cylinders (heaven / earth / underworld) on the diagonal, 3 px thick.
        segs = [(-3, 2), (4, 8), (10, 13)]
        for si, (d0, d1) in enumerate(segs):
            for d in range(d0, d1 + 1):
                for s in (14, 15, 16):
                    if (s + d) % 2:
                        continue
                    x, y = diag(s, d)
                    base = C("3A2A2A") if s != 14 else C("5A4444")
                    if s == 16:
                        base = C("241818")
                    put(img, x, y, base)
            # Red glyphs scrolling around each cylinder, alternating direction.
            direction = 1 if si == 1 else -1
            for d in range(d0, d1 + 1):
                phase = (d * 2 + direction * f * (2 if fast else 1)) % 5
                if phase == 0:
                    for s in (14, 16):
                        if (s + d) % 2 == 0:
                            put(img, *diag(s, d), RED_HI if fast else RED)
                elif phase == 2 and (15 + d) % 2 == 0:
                    put(img, *diag(15, d), RED if fast else RED_DK)
        # Gold rings between the segments.
        for d in (3, 9):
            for s in (13, 15, 17):
                if (s + d) % 2 == 0:
                    put(img, *diag(s, d), GOLD)
        for d in (2, 3, 8, 9):
            for s in (14, 16):
                if (s + d) % 2 == 0:
                    put(img, *diag(s, d), GOLD_MID)
        # Hilt: gold guard + dark grip + gold pommel.
        for d, ss in ((-5, (12, 14, 16, 18)), (-4, (13, 15, 17))):
            for s in ss:
                if (s + d) % 2 == 0:
                    put(img, *diag(s, d), GOLD)
        for s, d in ((15, -7), (16, -8), (15, -9)):
            put(img, *diag(s, d), C("5B2E12"))
        put(img, *diag(16, -10), GOLD)
        put(img, *diag(15, -11), GOLD_DK)
        outline(img, C("140A0A"))
        out.append(img)
    return out


def enkidu():
    img = new(16, 16)
    centres = [(2, 13), (4, 11), (6, 9), (8, 7), (10, 5)]
    for i, (cx, cy) in enumerate(centres):
        if i % 2 == 0:
            for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1), (-1, -1), (1, 1)):
                put(img, cx + dx, cy + dy, GOLD if dx <= 0 else GOLD_MID)
            put(img, cx - 1, cy - 1, GOLD_HI)
        else:
            put(img, cx, cy, GOLD_HI)
            put(img, cx + 1, cy - 1, GOLD)
            put(img, cx - 1, cy + 1, GOLD)
    # Wedge-shaped blade head.
    for p in ((11, 4), (12, 3), (13, 2), (14, 1), (12, 4), (11, 3), (13, 3), (12, 2)):
        put(img, *p, C("E6ECF5"))
    put(img, 14, 1, C("FFFFFF"))
    put(img, 13, 2, C("FFFFFF"))
    outline(img, GOLD_LINE)
    return img


def vimana():
    img = new(16, 16)
    # Boat hull (gold) seen from above-front.
    for y in range(8, 14):
        half = 7 - (y - 8)
        for x in range(8 - half, 8 + half):
            put(img, x, y, GOLD if y < 12 else GOLD_MID)
    for x in range(2, 14):
        put(img, x, 8, GOLD_HI)
    # Emerald wings.
    for i in range(6):
        put(img, 1 + i, 7 - i // 2, EMERALD)
        put(img, 14 - i, 7 - i // 2, EMERALD)
        put(img, 1 + i, 6 - i // 2, EMERALD_HI if i % 2 else EMERALD)
        put(img, 14 - i, 6 - i // 2, EMERALD_HI if i % 2 else EMERALD)
    # Throne back.
    for y in range(3, 8):
        put(img, 7, y, GOLD)
        put(img, 8, y, GOLD_MID)
    put(img, 7, 2, RED)
    put(img, 8, 2, RED_DK)
    for x in (5, 10):
        put(img, x, 12, EMERALD)
    outline(img, GOLD_LINE)
    return img


def elixir():
    img = new(16, 16)
    glass = C("DDE6F2", 200)
    for y in range(6, 15):
        w = 3 if y < 8 else 5
        for x in range(8 - w, 8 + w):
            put(img, x, y, glass)
    for y in range(8, 14):
        for x in range(4, 12):
            put(img, x, y, GOLD if (x + y) % 3 else GOLD_HI)
    for x in range(6, 10):
        put(img, x, 3, GOLD_DK)
        put(img, x, 4, GOLD)
        put(img, x, 5, glass)
    put(img, 5, 9, C("FFFFFF"))
    put(img, 5, 10, C("FFFFFF", 200))
    outline(img, GOLD_LINE)
    return img


def grail_mud():
    img = new(16, 16)
    # Golden chalice with black-red mud spilling over.
    for y in range(4, 9):
        w = 5 - (y - 4) // 2
        for x in range(8 - w, 8 + w):
            put(img, x, y, GOLD if x < 8 else GOLD_MID)
    for y in range(9, 12):
        put(img, 7, y, GOLD)
        put(img, 8, y, GOLD_MID)
    for x in range(5, 11):
        put(img, x, 12, GOLD_DK)
        put(img, x, 13, GOLD)
    mud = [(3, 3), (4, 3), (5, 3), (6, 3), (7, 3), (8, 3), (9, 3), (10, 3), (11, 3), (12, 3), (4, 2), (7, 2), (10, 2),
           (3, 4), (12, 4), (3, 5), (12, 6), (12, 7), (3, 6)]
    for p in mud:
        put(img, *p, C("1A0A0E"))
    for p in ((6, 2), (9, 3), (12, 5)):
        put(img, *p, C("6A0A1A"))
    outline(img, C("120606"))
    return img


def knight_barding():
    img = new(16, 16)
    shape = ["................",
             "...........SSS..",
             "..........SSHSS.",
             "..........SSSS..",
             "...........SS...",
             "..SSSSSSSSSS....",
             ".SHHSSSSSSSSS...",
             ".GGGGGGGGGGGG...",
             ".BBBBBBBBBBBB...",
             ".BDBBBBBBBBDB...",
             ".BBBBBGBBBBBB...",
             ".BDBBBBBBBBDB...",
             ".GBGGBGGBGGBG...",
             "................",
             "................",
             "................"]
    cmap = {"S": SILVER, "H": SILVER_HI, "B": DRESS, "D": DRESS_DK, "G": GUARD}
    for y, row in enumerate(shape):
        for x, ch in enumerate(row):
            if ch in cmap:
                put(img, x, y + 1, cmap[ch])
    outline(img, NAVY_LINE)
    return img


def warhorse():
    img = new(16, 16)
    # White horse head in profile with a blue bridle and a silver chamfron.
    shape = ["................",
             "......WW........",
             ".....WWWW.......",
             "....WWWWWW......",
             "...WWWSSSWW.....",
             "...WWSSSSSWW....",
             "..WWWWBWWWWWW...",
             "..WWWWBWWWWWWW..",
             "..WWWWBWWWWWWWW.",
             "...WWWBBBBBWWWW.",
             "...WWWW...WWWWW.",
             "..MMWWW....WWW..",
             ".MMM.WWW........",
             ".MM...WW........",
             "................",
             "................"]
    cmap = {"W": C("F4F4F0"), "S": SILVER, "B": DRESS, "M": C("D8D2C4")}
    for y, row in enumerate(shape):
        for x, ch in enumerate(row):
            if ch in cmap:
                c = cmap[ch]
                if ch == "W" and x > 11:
                    c = C("E2E2DC")
                put(img, x, y, c)
    put(img, 5, 5, C("202030"))
    put(img, 7, 4, GUARD)
    outline(img, C("3C3C50"))
    return img


def armor_icon(kind):
    img = new(16, 16)
    hero = kind.startswith("golden")
    main = GOLD if hero else SILVER
    hi = GOLD_HI if hero else SILVER_HI
    dk = GOLD_DK if hero else SILVER_DK
    line = GOLD_LINE if hero else NAVY_LINE
    if kind == "golden_crown":
        for x in range(3, 13):
            put(img, x, 10, GOLD_MID)
            put(img, x, 9, GOLD)
            put(img, x, 11, GOLD_DK)
        for i, x in enumerate(range(3, 13, 3)):
            for y in range(4 + (i % 2) * 2, 9):
                put(img, x, y, GOLD_HI if y < 7 else GOLD)
                put(img, x + 1, y, GOLD if y < 7 else GOLD_MID)
        put(img, 7, 10, RED)
        put(img, 8, 10, RED_DK)
        # Spiked blond hair rising behind the crown.
        for p in ((5, 2), (6, 3), (9, 2), (10, 3), (12, 3), (4, 4)):
            put(img, *p, HAIR)
    elif kind in ("golden_chestplate", "knight_breastplate"):
        rows = ["..XX......XX..",
                ".XXXX....XXXX.",
                "XXXXXXXXXXXXXX",
                "XXXXXXXXXXXXXX",
                ".XXXXXXXXXXXX.",
                "..XXXXXXXXXX..",
                "..XXXXXXXXXX..",
                "..XXXXXXXXXX..",
                "..XXXXXXXXXX..",
                "...XXXXXXXX...",
                "...XXXXXXXX..."]
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch == "X":
                    c = main
                    if y < 2 or x in (2, 3) and y > 2:
                        c = hi
                    if x > 10 and y > 2:
                        c = dk
                    put(img, x + 1, y + 3, c)
        if hero:
            # Cuneiform strokes on the chest.
            for (x, y) in ((6, 7), (7, 7), (9, 7), (6, 9), (8, 9), (9, 9), (7, 11), (8, 11)):
                put(img, x, y, GOLD_DK)
            put(img, 1, 3, GOLD_HI)
            put(img, 14, 3, GOLD_HI)
        else:
            for y in range(9, 14):
                for x in range(4, 12):
                    put(img, x, y, DRESS if y > 10 else DRESS_HI)
            for x in range(3, 13):
                put(img, x, 8, GUARD)
    elif kind in ("golden_greaves", "knight_skirt"):
        for y in range(3, 14):
            for x in range(3, 13):
                if y > 6 and 7 <= x <= 8:
                    continue
                c = main if x < 10 else dk
                if x in (3, 4):
                    c = hi
                put(img, x, y, c)
        for x in range(3, 13):
            put(img, x, 3, GOLD_DK if hero else GUARD)
        if hero:
            for y in range(4, 11):
                put(img, 7, y, RED if y < 9 else RED_DK)
                put(img, 8, y, RED_DK)
        else:
            for y in range(4, 9):
                for x in range(3, 13):
                    if not (y > 6 and 7 <= x <= 8):
                        put(img, x, y, DRESS if x < 10 else DRESS_DK)
    elif kind in ("golden_sabatons", "knight_boots"):
        for side in (0, 1):
            ox = 2 + side * 7
            for y in range(5, 13):
                for x in range(ox, ox + 4):
                    put(img, x, y, main if x < ox + 3 else dk)
            for x in range(ox - (1 if side == 0 else 0), ox + 5 + (1 if hero and side == 1 else 0)):
                put(img, x, 12, dk)
                put(img, x, 13, main)
            put(img, ox, 5, hi)
            put(img, ox + 1, 5, hi)
            put(img, ox, 9, GUARD if not hero else GOLD_HI)
            put(img, ox + 1, 9, GUARD if not hero else GOLD_HI)
            if hero:
                put(img, ox + 5 if side == 1 else ox - 2, 13, GOLD_HI)
    elif kind == "knight_ribbon":
        # Blonde bun with a navy ribbon and the famous ahoge.
        for y in range(5, 12):
            for x in range(4, 12):
                if (x - 7.5) ** 2 / 16 + (y - 8.5) ** 2 / 12 <= 1:
                    put(img, x, y, HAIR if (x + y) % 4 else HAIR_HI)
        for p in ((2, 7), (3, 7), (2, 8), (3, 9), (12, 7), (13, 7), (12, 9), (13, 8)):
            put(img, *p, DRESS)
        for p in ((4, 8), (11, 8), (4, 9), (11, 9)):
            put(img, *p, DRESS_HI)
        for p in ((8, 4), (8, 3), (9, 2), (10, 2)):
            put(img, *p, HAIR)
    outline(img, line)
    return img


def spawn_egg(shell, shell_dk, spots, seed):
    rnd = random.Random(seed)
    img = new(16, 16)
    for y in range(16):
        for x in range(16):
            nx, ny = (x - 7.5) / 5.3, (y - 8.6) / 6.6
            if ny < 0:
                nx *= 1.0 + 0.35 * -ny
            if nx * nx + ny * ny <= 1.0:
                light = max(0.0, min(1.0, 0.55 - 0.5 * (nx * 0.8 + ny * 0.6)))
                put(img, x, y, mix(shell_dk, shell, light))
    for c in spots:
        for _ in range(4):
            while True:
                x, y = rnd.randint(4, 11), rnd.randint(4, 13)
                if img.getpixel((x, y))[3] > 0 and img.getpixel((x + 1, y))[3] > 0:
                    break
            put(img, x, y, c)
            if rnd.random() < 0.5:
                put(img, x + 1, y, c)
    put(img, 5, 5, C("FFFFFF", 200))
    put(img, 5, 6, C("FFFFFF", 120))
    outline(img, C("1A1A22"))
    return img


# ---------------------------------------------------------------------------------------------
# Masks from vanilla textures (only alpha is used)
# ---------------------------------------------------------------------------------------------

def vanilla_mask(path):
    with zipfile.ZipFile(CLIENT_JAR) as z:
        im = Image.open(io.BytesIO(z.read(path))).convert("RGBA")
    return [[im.getpixel((x, y))[3] > 0 for x in range(im.width)] for y in range(im.height)], im.size


def paint_masked(size, mask, painter):
    img = new(*size)
    for y in range(size[1]):
        for x in range(size[0]):
            if mask[y][x]:
                c = painter(x, y)
                if c is not None:
                    put(img, x, y, c)
    return img


# Armour UV faces on the 64x32 layout, as (x0, y0, x1, y1) rectangles.
HEAD = {"top": (8, 0, 16, 8), "bottom": (16, 0, 24, 8), "right": (0, 8, 8, 16), "front": (8, 8, 16, 16), "left": (16, 8, 24, 16), "back": (24, 8, 32, 16)}
BODY = {"top": (20, 16, 28, 20), "bottom": (28, 16, 36, 20), "right": (16, 20, 20, 32), "front": (20, 20, 28, 32), "left": (28, 20, 32, 32), "back": (32, 20, 40, 32)}
ARM = {"top": (44, 16, 48, 20), "bottom": (48, 16, 52, 20), "right": (40, 20, 44, 32), "front": (44, 20, 48, 32), "left": (48, 20, 52, 32), "back": (52, 20, 56, 32)}
LEG = {"top": (4, 16, 8, 20), "bottom": (8, 16, 12, 20), "right": (0, 20, 4, 32), "front": (4, 20, 8, 32), "left": (8, 20, 12, 32), "back": (12, 20, 16, 32)}


def face_of(x, y, part):
    for name, (x0, y0, x1, y1) in part.items():
        if x0 <= x < x1 and y0 <= y < y1:
            return name, x - x0, y - y0, x1 - x0, y1 - y0
    return None


def hero_armor():
    mask, size = vanilla_mask("assets/minecraft/textures/entity/equipment/humanoid/gold.png")
    rnd = random.Random(11)

    def painter(x, y):
        f = face_of(x, y, HEAD)
        if f:
            name, u, v, w, h = f
            if name == "top":
                return HAIR_HI if (u * 3 + v) % 5 == 0 else HAIR
            if name == "front":
                if v <= 1:
                    return GOLD_HI if v == 0 else (RED if u in (3, 4) else GOLD)
                return None
            # Sides / back: golden band low, spiky blond hair above it.
            if v >= 6:
                return GOLD if v == 6 else GOLD_DK
            spike = (u + (1 if name == "back" else 0)) % 3
            if v < 2 and spike == 0:
                return HAIR_HI
            return HAIR if (u + v) % 3 else HAIR_DK
        f = face_of(x, y, BODY)
        if f:
            name, u, v, w, h = f
            if name == "front":
                if u in (0, w - 1):
                    return GOLD_DK
                if v in (4, 7) and 1 <= u <= 6:
                    return GOLD_DK  # cuneiform bands
                if v in (5, 6) and u in (2, 4, 6):
                    return GOLD_LINE
                if v == 0:
                    return GOLD_HI
                return GOLD if (u + v) % 4 else GOLD_MID
            if name == "back":
                return GOLD_MID if (u + v) % 3 else GOLD
            return GOLD_MID
        f = face_of(x, y, ARM)
        if f:
            name, u, v, w, h = f
            if v <= 3:  # flared pauldron
                return GOLD_HI if v == 0 else (GOLD if v < 3 else GOLD_DK)
            if v >= 8:
                return GOLD if (u + v) % 2 else GOLD_MID
            return C("2A1A12") if name in ("front", "back") and v in (5, 6) else GOLD_MID
        f = face_of(x, y, LEG)
        if f:  # boots
            name, u, v, w, h = f
            if v >= 11:
                return GOLD_DK
            return GOLD_HI if v == 6 else (GOLD if (u + v) % 3 else GOLD_MID)
        return GOLD

    return paint_masked(size, mask, painter)


def hero_leggings():
    mask, size = vanilla_mask("assets/minecraft/textures/entity/equipment/humanoid_leggings/gold.png")

    def painter(x, y):
        f = face_of(x, y, BODY)
        if f:
            name, u, v, w, h = f
            if name == "back" and v >= 7:
                return RED if (u + v) % 5 else RED_HI  # red loincloth at the back
            if name in ("left", "right") and v >= 8:
                return RED_DK
            return GOLD_DK if v == 6 else GOLD
        f = face_of(x, y, LEG)
        if f:
            name, u, v, w, h = f
            if name == "back" and v <= 6:
                return RED
            if v in (0, 1) and name != "top":
                return GOLD_DK
            return GOLD_HI if u == 0 and name == "front" else (GOLD if (u + v) % 4 else GOLD_MID)
        return GOLD

    return paint_masked(size, mask, painter)


def knight_armor():
    mask, size = vanilla_mask("assets/minecraft/textures/entity/equipment/humanoid/gold.png")

    def painter(x, y):
        f = face_of(x, y, HEAD)
        if f:
            name, u, v, w, h = f
            if name == "top":
                if u == 4 and v in (1, 2):
                    return HAIR_HI  # ahoge root
                return HAIR if (u + v) % 4 else HAIR_HI
            if name == "front":
                if v <= 1:
                    return HAIR if v == 0 else (HAIR_DK if u % 3 == 0 else HAIR)
                return None
            if name == "back":
                # Braided bun wrapped with the navy ribbon.
                if 2 <= u <= 5 and 2 <= v <= 5:
                    if v == 3 or v == 4:
                        return DRESS if u in (2, 5) else DRESS_HI
                    return HAIR_DK if (u + v) % 2 else HAIR
                return HAIR if (u + v) % 3 else HAIR_DK
            return HAIR if v < 6 or u % 2 else HAIR_DK
        f = face_of(x, y, BODY)
        if f:
            name, u, v, w, h = f
            if name == "front":
                if v <= 5:  # silver breastplate with a gold rim
                    if v == 5 or u in (0, w - 1):
                        return GUARD
                    return SILVER_HI if v == 0 or u in (1, 2) else SILVER
                return DRESS if u % 3 else DRESS_HI
            if name == "back":
                return DRESS if v > 1 else SILVER
            return SILVER_DK if v <= 5 else DRESS_DK
        f = face_of(x, y, ARM)
        if f:
            name, u, v, w, h = f
            if v <= 2:
                return SILVER_HI if v == 0 else SILVER  # pauldron
            if v >= 7:
                return GUARD if v == 7 else (SILVER if (u + v) % 3 else SILVER_HI)  # gauntlet
            return DRESS if (u + v) % 3 else DRESS_HI
        f = face_of(x, y, LEG)
        if f:
            name, u, v, w, h = f
            if v == 7:
                return GUARD
            return SILVER if (u + v) % 3 else SILVER_HI
        return SILVER

    return paint_masked(size, mask, painter)


def knight_leggings():
    mask, size = vanilla_mask("assets/minecraft/textures/entity/equipment/humanoid_leggings/gold.png")

    def painter(x, y):
        f = face_of(x, y, BODY)
        if f:
            name, u, v, w, h = f
            if name == "left" and 7 <= v <= 11:
                return GUARD if v % 2 else KNIGHT_BLUE  # Avalon hanging at the left hip
            if v == 6:
                return GUARD
            return DRESS if (u + v) % 3 else DRESS_HI
        f = face_of(x, y, LEG)
        if f:
            name, u, v, w, h = f
            if name in ("front", "back") and 1 <= v <= 6:
                return SILVER if v != 6 else GUARD  # skirt plates
            return DRESS if (u + v) % 4 else DRESS_DK
        return DRESS

    return paint_masked(size, mask, painter)


def knight_horse_armor():
    mask, size = vanilla_mask("assets/minecraft/textures/entity/equipment/horse_body/diamond.png")
    rnd = random.Random(5)

    def painter(x, y):
        edge = not all(0 <= x + dx < size[0] and 0 <= y + dy < size[1] and mask[y + dy][x + dx]
                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge:
            return GUARD
        if y >= 54 or (y % 8) < 3:
            return DRESS if (x + y) % 3 else DRESS_HI
        return SILVER if rnd.random() > 0.2 else SILVER_HI

    return paint_masked(size, mask, painter)


# ---------------------------------------------------------------------------------------------
# NPC skins (64x64 player layout)
# ---------------------------------------------------------------------------------------------

def skin_faces(ox, oy, w, h, d):
    """UV faces of a cube (width w, height h, depth d) whose texture origin is (ox, oy)."""
    return {"top": (ox + d, oy, ox + d + w, oy + d), "bottom": (ox + d + w, oy, ox + d + 2 * w, oy + d),
            "right": (ox, oy + d, ox + d, oy + d + h), "front": (ox + d, oy + d, ox + d + w, oy + d + h),
            "left": (ox + d + w, oy + d, ox + 2 * d + w, oy + d + h), "back": (ox + 2 * d + w, oy + d, ox + 2 * d + 2 * w, oy + d + h)}


def fill_faces(img, faces, painter):
    for name, (x0, y0, x1, y1) in faces.items():
        for y in range(y0, y1):
            for x in range(x0, x1):
                c = painter(name, x - x0, y - y0, x1 - x0, y1 - y0)
                if c is not None:
                    put(img, x, y, c)


def gil_skin():
    img = new(64, 64)
    head = skin_faces(0, 0, 8, 8, 8)
    hat = skin_faces(32, 0, 8, 8, 8)

    def head_p(n, u, v, w, h):
        if n == "top":
            return HAIR if (u + v) % 3 else HAIR_HI
        if n == "front":
            if v <= 1:
                return HAIR if (u + v) % 2 else HAIR_HI
            if v == 3 and u in (1, 2, 5, 6):
                return C("FFFFFF") if u in (1, 6) else C("C01020")  # red eyes
            if v == 3 and u in (0, 7):
                return SKIN_DK
            if v == 6 and 3 <= u <= 4:
                return C("B07A6A")
            return SKIN
        if n in ("left", "right"):
            return HAIR if v < 3 or (n == "left" and u > 5) or (n == "right" and u < 2) else SKIN
        if n == "back":
            return HAIR if (u + v) % 3 else HAIR_DK
        return SKIN

    fill_faces(img, head, head_p)

    def hat_p(n, u, v, w, h):
        # Spiky swept-back hair in the overlay layer.
        if n == "top":
            return HAIR_HI if (u + 2 * v) % 4 == 0 else (HAIR if (u * v) % 3 else None)
        if n == "front":
            return HAIR_HI if v == 0 and u % 2 == 0 else None
        if n in ("left", "right", "back"):
            if v <= 2:
                return HAIR_HI if (u + v) % 2 == 0 else HAIR
            return None
        return None

    fill_faces(img, hat, hat_p)

    def body_p(n, u, v, w, h):
        if n == "front":
            if v in (3, 7):
                return GOLD_DK
            if v in (4, 5) and u % 2 == 0:
                return GOLD_LINE
            if v >= 10:
                return GOLD_DK if (u + v) % 2 else GOLD
            return GOLD if (u + v) % 4 else GOLD_HI
        if n == "back":
            return RED if v >= 8 else GOLD_MID
        return GOLD_MID

    fill_faces(img, skin_faces(16, 16, 8, 12, 4), body_p)

    def arm_p(n, u, v, w, h):
        if v < 4:
            return GOLD_HI if v == 0 else GOLD
        if v >= 10:
            return GOLD
        return GOLD_MID if (u + v) % 3 else GOLD_DK

    fill_faces(img, skin_faces(40, 16, 4, 12, 4), arm_p)
    fill_faces(img, skin_faces(32, 48, 4, 12, 4), arm_p)

    def leg_p(n, u, v, w, h):
        if n == "back" and v < 7:
            return RED
        if v >= 10:
            return GOLD_DK
        return GOLD if (u + v) % 3 else GOLD_MID

    fill_faces(img, skin_faces(0, 16, 4, 12, 4), leg_p)
    fill_faces(img, skin_faces(16, 48, 4, 12, 4), leg_p)

    # Pauldron flare in the sleeve overlay.
    def sleeve_p(n, u, v, w, h):
        if v < 3 and n != "bottom":
            return GOLD_HI if v == 0 else GOLD
        return None

    fill_faces(img, skin_faces(40, 32, 4, 12, 4), sleeve_p)
    fill_faces(img, skin_faces(48, 48, 4, 12, 4), sleeve_p)
    return img


def saber_skin():
    img = new(64, 64)
    head = skin_faces(0, 0, 8, 8, 8)
    hat = skin_faces(32, 0, 8, 8, 8)

    def head_p(n, u, v, w, h):
        if n == "top":
            return HAIR if (u + v) % 4 else HAIR_HI
        if n == "front":
            if v <= 1 or (v == 2 and u in (0, 3, 4, 7)):
                return HAIR if (u + v) % 3 else HAIR_HI
            if v == 4 and u in (1, 2, 5, 6):
                return C("FFFFFF") if u in (1, 6) else C("1E8A5A")  # green eyes
            if v == 6 and 3 <= u <= 4:
                return C("D08C84")
            if u in (0, 7) and v <= 5:
                return HAIR
            return SKIN
        if n in ("left", "right"):
            return HAIR if v < 4 or (n == "left" and u > 4) or (n == "right" and u < 3) else SKIN
        if n == "back":
            if 2 <= u <= 5 and 3 <= v <= 6:
                return DRESS if v == 4 else (HAIR_DK if (u + v) % 2 else HAIR)
            return HAIR if (u + v) % 3 else HAIR_DK
        return SKIN

    fill_faces(img, head, head_p)

    def hat_p(n, u, v, w, h):
        if n == "top" and u == 4 and v in (1, 2):
            return HAIR_HI  # ahoge
        if n == "back" and 2 <= u <= 5 and 2 <= v <= 6:
            return DRESS_HI if v in (4,) else (HAIR if (u + v) % 2 else HAIR_HI)  # bun + ribbon
        if n == "front" and v == 0 and u in (2, 5):
            return HAIR_HI
        return None

    fill_faces(img, hat, hat_p)

    def body_p(n, u, v, w, h):
        if n == "front":
            if v <= 5:
                if v == 5 or u in (0, w - 1):
                    return GUARD
                return SILVER_HI if v == 0 or u in (1, 2) else SILVER
            return DRESS if u % 3 else DRESS_HI
        if n == "back":
            return DRESS if v > 1 else SILVER
        if n == "left" and v >= 8:
            return GUARD if v % 2 else KNIGHT_BLUE  # scabbard
        return DRESS_DK if v > 5 else SILVER_DK

    fill_faces(img, skin_faces(16, 16, 8, 12, 4), body_p)

    def arm_p(n, u, v, w, h):
        if v <= 2:
            return SILVER_HI if v == 0 else SILVER
        if v >= 7:
            if v == 7:
                return GUARD
            return SILVER if (u + v) % 3 else SILVER_HI
        return DRESS if (u + v) % 3 else DRESS_HI

    # Slim arms: 3 px wide.
    fill_faces(img, skin_faces(40, 16, 3, 12, 4), arm_p)
    fill_faces(img, skin_faces(32, 48, 3, 12, 4), arm_p)

    def leg_p(n, u, v, w, h):
        if v <= 5:
            return DRESS if (u + v) % 3 else DRESS_HI
        if v == 6:
            return GUARD
        return SILVER if (u + v) % 3 else SILVER_HI

    fill_faces(img, skin_faces(0, 16, 4, 12, 4), leg_p)
    fill_faces(img, skin_faces(16, 48, 4, 12, 4), leg_p)

    def pants_p(n, u, v, w, h):
        # Skirt flare over the thighs.
        if v <= 5 and n != "top" and n != "bottom":
            return DRESS if (u + v) % 2 else DRESS_DK
        return None

    fill_faces(img, skin_faces(0, 32, 4, 12, 4), pants_p)
    fill_faces(img, skin_faces(0, 48, 4, 12, 4), pants_p)
    return img


# ---------------------------------------------------------------------------------------------
# Effect textures
# ---------------------------------------------------------------------------------------------

def gate_ripple():
    n = 64
    img = new(n, n)
    for y in range(n):
        for x in range(n):
            r = math.hypot(x - 31.5, y - 31.5) / 31.5
            if r > 1:
                continue
            rings = 0.5 + 0.5 * math.cos(r * math.pi * 7.0)
            edge = max(0.0, 1.0 - r) ** 0.4
            a = int(255 * min(1.0, (0.25 + 0.75 * rings) * edge * (1.2 if r > 0.15 else 1.6)))
            c = mix(C("FFD34A"), C("FFF6C8"), rings)
            put(img, x, y, with_alpha(c, a))
    return img


def labyrinth():
    """Circular maze of red lines (Bab-ilu's sky pattern): concentric rings with gaps + radial walls."""
    n = 256
    img = new(n, n)
    rnd = random.Random(1337)
    c0 = n / 2 - 0.5
    rings = 12
    gaps = {}
    for k in range(1, rings + 1):
        gaps[k] = [rnd.uniform(0, math.tau) for _ in range(1 + k // 3)]
    walls = []
    for k in range(1, rings):
        for _ in range(3 + k):
            walls.append((k, rnd.uniform(0, math.tau)))
    for y in range(n):
        for x in range(n):
            dx, dy = x - c0, y - c0
            r = math.hypot(dx, dy) / (n / 2) * rings
            if r > rings + 0.6:
                continue
            ang = math.atan2(dy, dx) % math.tau
            val = 0.0
            k = round(r)
            if 1 <= k <= rings and abs(r - k) < 0.12:
                if all(min(abs(ang - g), math.tau - abs(ang - g)) > 0.35 / k for g in gaps[k]):
                    val = 1.0
            for wk, wa in walls:
                if wk <= r <= wk + 1:
                    da = min(abs(ang - wa), math.tau - abs(ang - wa)) * r
                    if da < 0.11:
                        val = 1.0
            if val > 0:
                put(img, x, y, C("FF2A2A", 235))
            elif r < rings + 0.4:
                glow = 0.5 + 0.5 * math.cos(r * math.pi)
                put(img, x, y, C("8A0010", int(35 + 25 * glow)))
    return img


def chain_link():
    img = new(16, 16)
    for y in range(16):
        for x in range(16):
            dx, dy = (x - 7.5) / 3.6, (y - 7.5) / 7.2
            r = dx * dx + dy * dy
            if 0.45 <= r <= 1.0:
                put(img, x, y, GOLD_HI if x < 7 else (GOLD if x < 10 else GOLD_DK))
    return img


def avalon_shard():
    img = new(16, 16)
    for y in range(16):
        for x in range(16):
            dx, dy = abs(x - 7.5), abs(y - 7.5)
            if dx / 7.5 + dy / 4.0 <= 1.0:
                t = dx / 7.5
                c = mix(C("FFF3C4"), C("7FB2FF"), t)
                put(img, x, y, with_alpha(c, int(230 - 120 * t)))
    return img


def golden_cracks():
    """Screen overlay for Gojo after Excalibur: gold cracks from the edges, transparent centre."""
    n = 256
    img = new(n, n)
    rnd = random.Random(7)
    for i in range(22):
        side = i % 4
        t = rnd.random()
        x, y = [(t * n, 0), (n - 1, t * n), (t * n, n - 1), (0, t * n)][side]
        ang = [math.pi / 2, math.pi, -math.pi / 2, 0][side] + rnd.uniform(-0.6, 0.6)
        length = rnd.uniform(30, 90)
        steps = int(length)
        for s in range(steps):
            ang += rnd.uniform(-0.35, 0.35)
            x += math.cos(ang)
            y += math.sin(ang)
            a = int(255 * (1 - s / steps))
            put(img, int(x), int(y), C("FFD24A", a))
            put(img, int(x) + 1, int(y), C("FFF2B0", a // 2))
            if rnd.random() < 0.04:
                ang += rnd.choice((-1, 1)) * 0.9
    return img


def vimana_model_texture():
    """64x64 atlas for the code-built Vimana model: gold areas top, emerald areas bottom."""
    img = new(64, 64)
    rnd = random.Random(3)
    for y in range(64):
        for x in range(64):
            if y < 32:
                c = GOLD if rnd.random() > 0.25 else (GOLD_HI if rnd.random() > 0.5 else GOLD_MID)
                if y % 8 == 7:
                    c = GOLD_DK
            elif y < 48:
                c = EMERALD if rnd.random() > 0.3 else EMERALD_HI
                if x % 8 == 0:
                    c = EMERALD_DK
            else:
                c = RED if rnd.random() > 0.2 else RED_DK
            put(img, x, y, c)
    return img


def mod_icon():
    base = new(32, 32, C("10131F"))
    # Left: gold ripple; right: blue glow. Two swords crossed.
    for y in range(32):
        for x in range(32):
            r = math.hypot(x - 8, y - 12)
            if r < 11 and int(r) % 3 == 0:
                put(base, x, y, mix(C("10131F"), GOLD, 0.55))
            r2 = math.hypot(x - 24, y - 12)
            if r2 < 12:
                put(base, x, y, mix(C("10131F"), C("2C57C8"), 0.5 * (1 - r2 / 12)))
    ex = excalibur_revealed().resize((24, 24), Image.NEAREST)
    ea = ea_frames(1)[0].transpose(Image.FLIP_LEFT_RIGHT).resize((24, 24), Image.NEAREST)
    base.alpha_composite(ea, (0, 6))
    base.alpha_composite(ex, (8, 6))
    return base.resize((128, 128), Image.NEAREST)


# ---------------------------------------------------------------------------------------------
# EMIYA (Archer): the Red Shroud, the black bow, Kanshou & Bakuya, Unlimited Blade Works
# ---------------------------------------------------------------------------------------------

SHROUD = C("B3141C")
SHROUD_HI = C("E0303A")
SHROUD_DK = C("6E0A10")
ARMOR_BLACK = C("1E1E24")
ARMOR_GREY = C("3A3A44")
ARMOR_LINE = C("0E0E12")
TRIM = C("B8BCC8")
WHITE_HAIR = C("ECECF0")
WHITE_HAIR_DK = C("B8B8C2")
TAN = C("B07A55")
TAN_DK = C("8E5E40")
KAN = C("1A1A1E")
KAN_HI = C("4A4A54")
KAN_RED = C("C02020")
BAK = C("F0F2F6")
BAK_DK = C("A8B0C0")
BAK_BLUE = C("5A78B0")
BRONZE = C("B87A3A")
BRONZE_HI = C("E0A860")
BRONZE_DK = C("6A4018")


def bezier(p0, p1, p2, t):
    u = 1 - t
    return (u * u * p0[0] + 2 * u * t * p1[0] + t * t * p2[0], u * u * p0[1] + 2 * u * t * p1[1] + t * t * p2[1])


def black_bow(pull=0, arrow=None):
    """The black bow: limbs bowed to the top left, a grip wrapped in red; drawn to the bottom right as it pulls."""
    img = new(16, 16)
    p0, p1, p2 = (3, 14), (0, 0), (14, 3)
    for i in range(40):
        x, y = bezier(p0, p1, p2, i / 39)
        mid = abs(i / 39 - 0.5) < 0.12
        put(img, int(round(x)), int(round(y)), KAN_RED if mid else (KAN_HI if i % 7 == 0 else KAN))
    q = (8.5 + pull * 1.6, 8.5 + pull * 1.6)
    for a, b in ((p0, q), (q, p2)):
        for i in range(24):
            x = a[0] + (b[0] - a[0]) * i / 23
            y = a[1] + (b[1] - a[1]) * i / 23
            if img.getpixel((int(round(x)), int(round(y))))[3] == 0:
                put(img, int(round(x)), int(round(y)), C("D8D8E0"))
    if arrow == "sword":
        for d in range(-3, 9):
            put(img, *diag(int(q[0] + q[1]) - 1, d), C("C8CCD8") if d < 7 else C("F4F6FF"))
    elif arrow == "drill":
        for k, d in enumerate(range(-3, 11)):
            x, y = diag(int(q[0] + q[1]) - 1, d)
            put(img, x, y, C("E8302A") if k % 2 else C("FFF0E8"))
            if d > 5:
                put(img, x + 1, y, C("E8302A") if k % 2 == 0 else C("FFF0E8"))
    outline(img, ARMOR_LINE)
    return img


def twin_sword(black):
    """Kanshou (black, red hexagons) or Bakuya (white, blue hexagons): a broad curved blade, a short grip."""
    img = new(16, 16)
    blade, hi, mark = (KAN, KAN_HI, KAN_RED) if black else (BAK, C("FFFFFF"), BAK_BLUE)
    for d in range(-2, 13, 2):
        put(img, *diag(15, d), hi if d > 9 else blade)
        put(img, *diag(16, d + 1), blade)
        if -1 < d < 10:
            put(img, *diag(14, d + 1), blade if black else BAK_DK)
    for d in (1, 5, 9):
        put(img, *diag(15, d), mark)
    # Guard: a small taiji-like disc, then the wrapped grip.
    for s in range(13, 19):
        put(img, *diag(s, -4), C("2A2A30") if black else C("C8CCD8"))
    for s, d in ((16, -6), (15, -7), (16, -8)):
        put(img, *diag(s, d), KAN_RED if black else C("2A2A30"))
    put(img, *diag(15, -9), C("8A7A50"))
    outline(img, ARMOR_LINE if black else C("4A5060"))
    return img


def sword_arrow():
    img = new(16, 16)
    for d in range(-9, 13):
        put(img, *diag(15, d), C("F4F6FF") if d > 9 else C("C8CCD8"))
    for s in (14, 16):
        put(img, *diag(s, -5), C("8A8E9A"))
    outline(img, C("3A3A44"))
    return img


def caladbolg_arrow():
    """The fake spiral sword: a long drill twisted red and white, widening behind its point."""
    img = new(16, 16)
    for k, d in enumerate(range(-9, 13)):
        x, y = diag(15, d)
        put(img, x, y, C("E8302A") if (k // 2) % 2 else C("FFF0E8"))
        if -6 < d < 8:
            x2, y2 = diag(16 + (k % 2) * -2, d)
            put(img, x2, y2, C("FFF0E8") if (k // 2) % 2 else C("B81A1A"))
    outline(img, C("3A0A0A"))
    return img


def ubw_icon():
    """A sword standing in the hill before a bronze gear, embers at its foot."""
    img = new(16, 16)
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 6.5)
            ang = math.atan2(y - 6.5, x - 7.5)
            teeth = 6.0 + (0.9 if math.cos(ang * 8) > 0.3 else 0.0)
            if 2.6 < r < teeth:
                put(img, x, y, BRONZE_HI if (x + y) % 5 == 0 else (BRONZE if r < 5.5 else BRONZE_DK))
    for y in range(1, 13):
        put(img, 7, y, C("F4F6FF") if y < 4 else C("C8CCD8"))
        put(img, 8, y, C("8A8E9A"))
    for x in range(5, 11):
        put(img, x, 10, C("2A2A30"))
    for x in range(2, 14):
        put(img, x, 13, SHROUD_DK)
        put(img, x, 14, C("5A1010"))
    for p in ((4, 12), (11, 12), (6, 11)):
        put(img, *p, C("FF8A2A"))
    outline(img, ARMOR_LINE)
    return img


def shroud_icon(kind):
    img = new(16, 16)
    if kind == "shroud_headpiece":
        # Swept-back white hair with a dark band.
        for y in range(4, 12):
            for x in range(3, 13):
                if (x - 7.5) ** 2 / 25 + (y - 8.5) ** 2 / 14 <= 1:
                    put(img, x, y, WHITE_HAIR if (x + y) % 3 else WHITE_HAIR_DK)
        for p in ((4, 3), (6, 2), (8, 2), (10, 3), (12, 4), (5, 4), (9, 3)):
            put(img, *p, WHITE_HAIR)
        for x in range(3, 13):
            put(img, x, 10, ARMOR_BLACK)
    elif kind == "shroud_coat":
        rows = ["..XX......XX..", ".XXXX....XXXX.", "XXXXXXXXXXXXXX", "XXXXXXXXXXXXXX", ".XXXXXXXXXXXX.",
                "..XXXXXXXXXX..", "..XXXXXXXXXX..", "..XXXXXXXXXX..", "..XXXXXXXXXX..", "..XXXXXXXXXX..", "..XX......XX.."]
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch != "X":
                    continue
                c = SHROUD if not (4 <= x <= 9 and y >= 2) else (ARMOR_BLACK if (x + y) % 4 else ARMOR_GREY)
                if 4 <= x <= 9 and y in (4, 7):
                    c = TRIM
                if x in (0, 1) and y >= 2:
                    c = SHROUD_HI
                put(img, x + 1, y + 3, c)
    elif kind == "shroud_leggings":
        for y in range(3, 14):
            for x in range(3, 13):
                if y > 6 and 7 <= x <= 8:
                    continue
                put(img, x, y, ARMOR_BLACK if x < 10 else ARMOR_GREY)
        for x in range(3, 13):
            put(img, x, 3, TRIM)
        for y in range(4, 11):
            put(img, 2, y, SHROUD)
            put(img, 13, y, SHROUD_DK)
    else:  # boots
        for side in (0, 1):
            ox = 2 + side * 7
            for y in range(5, 13):
                for x in range(ox, ox + 4):
                    put(img, x, y, ARMOR_BLACK if x < ox + 3 else ARMOR_GREY)
            for x in range(ox, ox + 5):
                put(img, x, 12, ARMOR_GREY)
                put(img, x, 13, ARMOR_BLACK)
            put(img, ox, 8, TRIM)
            put(img, ox + 1, 8, TRIM)
    outline(img, ARMOR_LINE)
    return img


def archer_armor():
    mask, size = vanilla_mask("assets/minecraft/textures/entity/equipment/humanoid/gold.png")

    def painter(x, y):
        f = face_of(x, y, HEAD)
        if f:
            name, u, v, w, h = f
            if name == "top":
                return WHITE_HAIR if (u * 2 + v) % 5 else WHITE_HAIR_DK
            if name == "front":
                if v == 0:
                    return WHITE_HAIR if u % 2 == 0 else WHITE_HAIR_DK  # swept-back fringe
                return None
            if v <= 3 or name == "back":
                return WHITE_HAIR if (u + v) % 3 else WHITE_HAIR_DK
            return None
        f = face_of(x, y, BODY)
        if f:
            name, u, v, w, h = f
            if name == "front":
                if u in (0, w - 1):
                    return SHROUD  # the coat open at the front
                if v in (2, 6):
                    return TRIM
                return ARMOR_BLACK if (u + v) % 4 else ARMOR_GREY
            return SHROUD if (u + v) % 4 else SHROUD_HI
        f = face_of(x, y, ARM)
        if f:
            name, u, v, w, h = f
            if v <= 6:
                return SHROUD if (u + v) % 3 else SHROUD_HI  # the red sleeves
            if v == 7:
                return TRIM
            return ARMOR_BLACK if (u + v) % 3 else ARMOR_GREY
        f = face_of(x, y, LEG)
        if f:
            name, u, v, w, h = f
            if v == 7:
                return TRIM
            return ARMOR_BLACK if v < 11 else ARMOR_LINE
        return ARMOR_BLACK

    return paint_masked(size, mask, painter)


def archer_leggings():
    mask, size = vanilla_mask("assets/minecraft/textures/entity/equipment/humanoid_leggings/gold.png")

    def painter(x, y):
        f = face_of(x, y, BODY)
        if f:
            name, u, v, w, h = f
            if v == 6:
                return TRIM if name == "front" and u in (3, 4) else ARMOR_GREY  # belt and buckle
            if name in ("back", "left", "right") and v >= 7:
                return SHROUD if (u + v) % 3 else SHROUD_DK  # the coat's tails
            return ARMOR_BLACK
        f = face_of(x, y, LEG)
        if f:
            name, u, v, w, h = f
            if name == "back" and v <= 7:
                return SHROUD if (u + v) % 3 else SHROUD_DK
            return ARMOR_BLACK if (u + v) % 5 else ARMOR_GREY
        return ARMOR_BLACK

    return paint_masked(size, mask, painter)


def emiya_skin():
    img = new(64, 64)

    def head_p(n, u, v, w, h):
        if n == "top":
            return WHITE_HAIR if (u + v) % 3 else WHITE_HAIR_DK
        if n == "front":
            if v == 0 or (v == 1 and u in (0, 2, 5, 7)):
                return WHITE_HAIR
            if v == 4 and u in (1, 2, 5, 6):
                return C("E8E8EC") if u in (1, 6) else C("6A6E7A")  # steel-grey eyes
            if v == 3 and u in (1, 2, 5, 6):
                return TAN_DK  # a frown
            if v == 6 and 3 <= u <= 4:
                return C("7A4A34")
            return TAN
        if n in ("left", "right"):
            return WHITE_HAIR if v < 3 or (n == "left" and u > 5) or (n == "right" and u < 2) else TAN
        if n == "back":
            return WHITE_HAIR if (u + v) % 3 else WHITE_HAIR_DK
        return TAN

    fill_faces(img, skin_faces(0, 0, 8, 8, 8), head_p)

    def hat_p(n, u, v, w, h):
        # The white hair swept back and up in spikes.
        if n == "top":
            return WHITE_HAIR if (u + 2 * v) % 3 else (WHITE_HAIR_DK if (u * v) % 2 else None)
        if n == "front":
            return WHITE_HAIR if v == 0 and u % 2 == 1 else None
        if n in ("left", "right", "back"):
            if v <= 2:
                return WHITE_HAIR if (u + v) % 2 == 0 else WHITE_HAIR_DK
            return None
        return None

    fill_faces(img, skin_faces(32, 0, 8, 8, 8), hat_p)

    def body_p(n, u, v, w, h):
        if n == "front":
            if u in (0, w - 1):
                return SHROUD
            if v in (2, 6):
                return TRIM
            if v >= 9:
                return ARMOR_GREY if v == 9 else ARMOR_BLACK  # belt
            return ARMOR_BLACK if (u + v) % 4 else ARMOR_GREY
        return SHROUD if (u + v) % 4 else SHROUD_HI

    fill_faces(img, skin_faces(16, 16, 8, 12, 4), body_p)

    def jacket_p(n, u, v, w, h):
        # The coat's overlay: its open front edges and the long tails at the back.
        if n == "front" and u in (0, w - 1):
            return SHROUD_HI
        if n in ("back", "left", "right"):
            return SHROUD if (u + v) % 3 else SHROUD_DK
        return None

    fill_faces(img, skin_faces(16, 32, 8, 12, 4), jacket_p)

    def arm_p(n, u, v, w, h):
        if v < 6:
            return SHROUD if (u + v) % 3 else SHROUD_HI
        if v == 6:
            return TRIM
        if v >= 10:
            return TAN if n == "bottom" else ARMOR_BLACK
        return ARMOR_BLACK if (u + v) % 3 else ARMOR_GREY

    fill_faces(img, skin_faces(40, 16, 4, 12, 4), arm_p)
    fill_faces(img, skin_faces(32, 48, 4, 12, 4), arm_p)

    def sleeve_p(n, u, v, w, h):
        if v < 5 and n != "bottom":
            return SHROUD if (u + v) % 2 else SHROUD_DK
        return None

    fill_faces(img, skin_faces(40, 32, 4, 12, 4), sleeve_p)
    fill_faces(img, skin_faces(48, 48, 4, 12, 4), sleeve_p)

    def leg_p(n, u, v, w, h):
        if v == 8:
            return TRIM
        if v > 8:
            return ARMOR_LINE if v >= 11 else ARMOR_BLACK
        return ARMOR_BLACK if (u + v) % 4 else ARMOR_GREY

    fill_faces(img, skin_faces(0, 16, 4, 12, 4), leg_p)
    fill_faces(img, skin_faces(16, 48, 4, 12, 4), leg_p)

    def tails_p(n, u, v, w, h):
        if n == "back" and v <= 8:
            return SHROUD if (u + v) % 3 else SHROUD_DK
        if n in ("left", "right") and v <= 4:
            return SHROUD_DK
        return None

    fill_faces(img, skin_faces(0, 32, 4, 12, 4), tails_p)
    fill_faces(img, skin_faces(0, 48, 4, 12, 4), tails_p)
    return img


def rho_aias_petal():
    """One petal of the seven rings: a rounded leaf of pink-violet light with a bright rim."""
    n = 64
    img = new(n, n)
    for y in range(n):
        for x in range(n):
            dx, dy = (x - 31.5) / 31.5, (y - 31.5) / 31.5
            r = (dx / 0.62) ** 2 + (dy / 0.98) ** 2
            if r > 1.0:
                continue
            rim = r > 0.78
            vein = abs(dx) < 0.04
            c = C("FFFFFF") if rim else (C("FFE0F4") if vein else mix(C("FF9AD8"), C("C8A0FF"), (dy + 1) / 2))
            put(img, x, y, with_alpha(c, 240 if rim else int(150 + 60 * (1 - r))))
    return img


def twin_trail():
    img = new(64, 16)
    for y in range(16):
        a = max(0.0, 1.0 - abs(y - 7.5) / 8.0) ** 1.5
        for x in range(64):
            put(img, x, y, with_alpha(C("FFFFFF"), int(255 * a)))
    return img


def ubw_sky():
    """The marble's sky, top to bottom: hazy amber, a burning horizon, the dark red wasteland beyond the wall."""
    w, h = 256, 128
    img = new(w, h)
    rnd = random.Random(42)
    streaks = [(rnd.uniform(0.1, 0.45), rnd.uniform(0.004, 0.012), rnd.uniform(0, math.tau)) for _ in range(14)]
    for y in range(h):
        v = y / (h - 1)
        if v < 0.55:
            base = mix(C("F2B05A"), C("E2622A"), (v / 0.55) ** 1.4)
        elif v < 0.62:
            base = mix(C("E2622A"), C("8A1A10"), (v - 0.55) / 0.07)
        else:
            base = mix(C("8A1A10"), C("3A0A08"), min(1.0, (v - 0.62) / 0.38))
        for x in range(w):
            c = base
            for sv, sw, ph in streaks:
                d = abs(v - sv - 0.01 * math.sin(x / w * math.tau * 2 + ph))
                if d < sw:
                    c = mix(c, C("6A2A1A"), 0.35 * (1 - d / sw))
            put(img, x, y, with_alpha(c, 255))
    return img


def ubw_gear():
    n = 128
    img = new(n, n)
    for y in range(n):
        for x in range(n):
            dx, dy = x - 63.5, y - 63.5
            r = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            outer = 52 + (9 if math.cos(ang * 16) > 0.2 else 0)
            spoke = any(abs(((ang - k * math.pi / 3 + math.pi) % math.tau) - math.pi) * r < 4 for k in range(6))
            inside = r <= outer and (r >= 40 or 14 <= r <= 22 or spoke and r < 40)
            if not inside:
                continue
            edge = r > outer - 2 or 38 < r < 40 or 14 <= r <= 15
            c = BRONZE_DK if edge else (BRONZE_HI if (int(dx) + int(dy)) % 9 == 0 else BRONZE)
            put(img, x, y, with_alpha(c, 235))
    return img


# ---------------------------------------------------------------------------------------------

def preview(items, dest):
    """Items at 16x on snow-like and nether-like backgrounds (design doc 10.8 check)."""
    sc = 12
    cols = len(items)
    out = Image.new("RGBA", (cols * (16 * sc + 8), 2 * (16 * sc + 8)), (255, 255, 255, 255))
    for i, (name, im) in enumerate(items):
        big = im.resize((im.width * sc, im.height * sc), Image.NEAREST) if im.width == 16 else im.resize((16 * sc, 16 * sc), Image.NEAREST)
        for row, bg in enumerate(((236, 242, 248, 255), (64, 22, 22, 255))):
            tile = Image.new("RGBA", big.size, bg)
            tile.alpha_composite(big)
            out.paste(tile, (i * (16 * sc + 8), row * (16 * sc + 8)))
    out.save(dest)


def main():
    prev = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else None
    items = {}
    items["excalibur_revealed"] = save(excalibur_revealed(), "item/excalibur_revealed.png")
    air = excalibur_air()
    items["excalibur_air"] = save_anim(air, "item/excalibur_air.png", 2)
    rel = excalibur_release()
    items["excalibur_release"] = save_anim(rel, "item/excalibur_release.png", 1)
    items["gate_of_babylon"] = save(gate_of_babylon(), "item/gate_of_babylon.png")
    items["bab_ilu"] = save(bab_ilu(), "item/bab_ilu.png")
    items["ea"] = save_anim(ea_frames(4), "item/ea.png", 4)
    save_anim(ea_frames(4, fast=True), "item/ea_charging.png", 1)
    items["enkidu"] = save(enkidu(), "item/enkidu.png")
    items["vimana"] = save(vimana(), "item/vimana.png")
    items["treasury_elixir"] = save(elixir(), "item/treasury_elixir.png")
    items["grail_mud"] = save(grail_mud(), "item/grail_mud.png")
    items["knight_barding"] = save(knight_barding(), "item/knight_barding.png")
    items["warhorse"] = save(warhorse(), "item/warhorse.png")
    for kind in ("golden_crown", "golden_chestplate", "golden_greaves", "golden_sabatons",
                 "knight_ribbon", "knight_breastplate", "knight_skirt", "knight_boots"):
        items[kind] = save(armor_icon(kind), f"item/{kind}.png")
    items["gilgamesh_spawn_egg"] = save(spawn_egg(C("E7B52C"), C("B8861A"), [RED, RED_DK], 1), "item/gilgamesh_spawn_egg.png")
    items["artoria_spawn_egg"] = save(spawn_egg(C("22408F"), C("172C6B"), [SILVER_HI, GUARD], 2), "item/artoria_spawn_egg.png")

    save(hero_armor(), "entity/equipment/humanoid/golden_regalia.png")
    save(hero_leggings(), "entity/equipment/humanoid_leggings/golden_regalia.png")
    save(knight_armor(), "entity/equipment/humanoid/knight_regalia.png")
    save(knight_leggings(), "entity/equipment/humanoid_leggings/knight_regalia.png")
    save(knight_horse_armor(), "entity/equipment/horse_body/knight_barding.png")
    save(gil_skin(), "entity/gilgamesh.png")
    save(saber_skin(), "entity/artoria.png")

    # EMIYA
    items["black_bow"] = save(black_bow(), "item/black_bow.png")
    save(black_bow(1), "item/black_bow_pulling_0.png")
    save(black_bow(2, "sword"), "item/black_bow_pulling_1.png")
    save(black_bow(2, "drill"), "item/black_bow_caladbolg.png")
    items["kanshou"] = save(twin_sword(True), "item/kanshou.png")
    items["bakuya"] = save(twin_sword(False), "item/bakuya.png")
    items["unlimited_blade_works"] = save(ubw_icon(), "item/unlimited_blade_works.png")
    save(sword_arrow(), "item/sword_arrow.png")
    items["caladbolg_arrow"] = save(caladbolg_arrow(), "item/caladbolg_arrow.png")
    for kind in ("shroud_headpiece", "shroud_coat", "shroud_leggings", "shroud_boots"):
        items[kind] = save(shroud_icon(kind), f"item/{kind}.png")
    items["emiya_spawn_egg"] = save(spawn_egg(C("B3141C"), C("6E0A10"), [ARMOR_BLACK, WHITE_HAIR], 3), "item/emiya_spawn_egg.png")
    save(archer_armor(), "entity/equipment/humanoid/red_shroud.png")
    save(archer_leggings(), "entity/equipment/humanoid_leggings/red_shroud.png")
    save(emiya_skin(), "entity/emiya.png")
    save(rho_aias_petal(), "misc/rho_aias_petal.png")
    save(twin_trail(), "misc/twin_trail.png")
    save(ubw_sky(), "misc/ubw_sky.png")
    save(ubw_gear(), "misc/ubw_gear.png")

    save(gate_ripple(), "misc/gate_ripple.png")
    save(labyrinth(), "misc/labyrinth.png")
    save(chain_link(), "misc/chain_link.png")
    save(avalon_shard(), "misc/avalon_shard.png")
    save(golden_cracks(), "misc/golden_cracks.png")
    save(vimana_model_texture(), "entity/vimana.png")
    icon = mod_icon()
    icon.save(ROOT / "src" / "main" / "resources" / "assets" / "fatekings" / "icon.png")

    if prev:
        prev.mkdir(parents=True, exist_ok=True)
        preview(list(items.items())[:13], prev / "items_a.png")
        preview(list(items.items())[13:26], prev / "items_b.png")
        preview(list(items.items())[26:], prev / "items_c.png")
        preview([(f"air{i}", f) for i, f in enumerate(air)], prev / "excalibur_air_frames.png")
        preview([(f"rel{i}", f) for i, f in enumerate(rel)], prev / "excalibur_release_frames.png")
        for rel_path in ("entity/equipment/humanoid/golden_regalia.png", "entity/equipment/humanoid_leggings/golden_regalia.png",
                         "entity/equipment/humanoid/knight_regalia.png", "entity/equipment/humanoid_leggings/knight_regalia.png",
                         "entity/gilgamesh.png", "entity/artoria.png", "entity/equipment/horse_body/knight_barding.png",
                         "entity/equipment/humanoid/red_shroud.png", "entity/equipment/humanoid_leggings/red_shroud.png", "entity/emiya.png",
                         "misc/ubw_sky.png", "misc/ubw_gear.png", "misc/rho_aias_petal.png"):
            im = Image.open(TEX / rel_path)
            big = Image.new("RGBA", (im.width * 8, im.height * 8), (60, 60, 70, 255))
            big.alpha_composite(im.resize((im.width * 8, im.height * 8), Image.NEAREST))
            big.save(prev / ("tex_" + rel_path.replace("/", "_")))
        icon.save(prev / "icon.png")
    print("textures written to", TEX.relative_to(ROOT))


if __name__ == "__main__":
    main()
