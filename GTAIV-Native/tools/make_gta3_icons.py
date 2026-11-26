#!/usr/bin/env python3
"""GTA III mobile-style touch icons for the engine's action ids.
usage: make_gta3_icons.py <es2 icon dir (gta-3-style pack)> <out dir>
Icons present in the pack are mapped by name; missing ones are drawn in the
same style: dark round plate, black rim, gold glyph."""
import math, os, sys
from PIL import Image, ImageDraw

SRC, OUT = sys.argv[1], sys.argv[2]
os.makedirs(OUT, exist_ok=True)
S = 256
GOLD = (200, 160, 88, 255)
EDGE = (20, 18, 14, 255)

def load(name):
    return Image.open(os.path.join(SRC, name)).convert("RGBA").resize((S, S), Image.LANCZOS)

def plate():
    return load("circle.png")

def draw(fn):
    im = plate(); d = ImageDraw.Draw(im); fn(d); return im

def poly(d, pts, w=6):
    d.polygon(pts, fill=GOLD, outline=EDGE, width=w)

def rect(d, box, w=6, r=10):
    d.rounded_rectangle(box, radius=r, fill=GOLD, outline=EDGE, width=w)

def tri(cx, cy, size, angle):
    a = math.radians(angle)
    pts = []
    for k in (0, 120, 240):
        t = a + math.radians(k)
        pts.append((cx + size * math.cos(t), cy + size * math.sin(t)))
    return pts

def arrow(angle):  # reuse the pack's left arrow, rotated
    return load("left.png").rotate(angle, resample=Image.BICUBIC)

def glyph_only(name, scale, dx=0, dy=0):
    """Gold glyph of a pack icon without its plate (pixels that are gold)."""
    src = load(name); px = src.load()
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0)); po = out.load()
    for y in range(S):
        for x in range(S):
            r, g, b, a = px[x, y]
            dist = math.hypot(x - S / 2, y - S / 2)
            if a and dist < S * 0.40 and (r > 120 or (r < 60 and g < 60 and b < 60 and dist < S * 0.38)):
                po[x, y] = (r, g, b, a)
    w = int(S * scale)
    g = out.resize((w, w), Image.LANCZOS)
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    layer.paste(g, ((S - w) // 2 + dx, (S - w) // 2 + dy), g)
    return layer

def compose(*layers):
    im = plate()
    for l in layers: im.alpha_composite(l)
    return im

icons = {}
# --- straight from the pack
for out, src in {
    "fire": "shoot.png", "vehicle_fire": "shoot.png", "vehicle_alt_fire": "shoot.png",
    "attack": "punch.png", "jump_climb": "jump.png", "run_sprint": "run.png", "aim": "lockon.png",
    "context": "enterandexit.png", "accelerate": "accelerate.png", "brake": "break.png",
    "handbrake": "handbreak.png", "horn": "horn.png", "camera_cycle": "cam-toggle.png",
    "heli_yaw_left": "tank_left.png", "heli_yaw_right": "tank_right.png", "zoom_in": "zoom+.png",
    "zoom_out": "zoom-.png", "pause": "pause.png", "phone_left": "left.png", "phone_right": "right.png",
    "phone_accept": "drive-toggle.png", "phone_back": "tank_left.png", "weapon_previous": "left.png",
    "weapon_next": "right.png", "parachute_brake_left": "left.png", "parachute_brake_right": "right.png",
    "edit_done": "drive-toggle.png", "grenade": "throw.png",
}.items():
    icons[out] = load(src)

icons["phone_up"] = arrow(-90); icons["phone_down"] = arrow(90)
icons["heli_ascend"] = arrow(-90); icons["heli_descend"] = arrow(90)

# --- drawn in the same style
def reload_(d):
    d.arc((62, 62, 194, 194), 200, 520, fill=EDGE, width=26); d.arc((66, 66, 190, 190), 200, 520, fill=GOLD, width=18)
    poly(d, tri(168, 86, 30, 20), 5)
    rect(d, (112, 100, 144, 170), 5, 8)
icons["reload"] = draw(reload_)

def cover(d):
    for row in range(4):
        y = 150 + row * 0
    for (x0, y0, x1, y1) in [(58, 150, 128, 178), (130, 150, 198, 178), (78, 180, 148, 208), (150, 180, 198, 208), (58, 180, 76, 208)]:
        rect(d, (x0, y0, x1, y1), 4, 3)
    d.ellipse((104, 58, 144, 98), fill=GOLD, outline=EDGE, width=5)
    poly(d, [(98, 146), (110, 104), (140, 104), (160, 146)], 5)
icons["cover"] = draw(cover)

def crouch(d):
    d.ellipse((108, 62, 148, 102), fill=GOLD, outline=EDGE, width=5)
    poly(d, [(92, 160), (104, 108), (150, 108), (170, 136), (150, 150), (140, 132), (128, 160)], 5)
    poly(d, tri(128, 188, 22, 90), 5)
icons["crouch"] = draw(crouch)

def phone(d):
    rect(d, (92, 48, 164, 208), 6, 16)
    d.rounded_rectangle((104, 66, 152, 142), radius=4, fill=EDGE)
    for i in range(3):
        for j in range(3):
            d.ellipse((104 + j * 18, 152 + i * 16, 116 + j * 18, 162 + i * 16), fill=EDGE)
icons["phone"] = draw(phone)

def look_behind(d):
    d.ellipse((52, 92, 204, 164), fill=GOLD, outline=EDGE, width=6)
    d.ellipse((104, 100, 152, 156), fill=EDGE)
    d.ellipse((120, 112, 134, 126), fill=GOLD)
    d.arc((76, 150, 180, 214), 20, 160, fill=EDGE, width=14); poly(d, tri(78, 176, 18, 230), 4)
icons["look_behind"] = draw(look_behind)

def free_aim(d):
    d.ellipse((64, 64, 192, 192), outline=EDGE, width=22); d.ellipse((68, 68, 188, 188), outline=GOLD, width=14)
    for a, b in [((124, 40), (132, 100)), ((124, 156), (132, 216)), ((40, 124), (100, 132)), ((156, 124), (216, 132))]:
        rect(d, (*a, *b), 4, 2)
icons["free_aim"] = draw(free_aim)

def radio(direction):
    def f(d):
        for i in range(2):
            x = 100 + i * 46 if direction > 0 else 156 - i * 46
            poly(d, tri(x, 128, 38, 0 if direction > 0 else 180), 5)
    return f
icons["radio_next"] = draw(radio(1)); icons["radio_previous"] = draw(radio(-1))

def headlights(d):
    d.pieslice((64, 72, 152, 184), 90, 270, fill=GOLD, outline=EDGE, width=6)
    rect(d, (104, 72, 120, 184), 0, 0)
    for k in range(4):
        y = 86 + k * 28
        rect(d, (136, y, 196, y + 14), 4, 4)
icons["headlights"] = draw(headlights)

def wheel(d):
    for k in range(8):
        a = math.radians(k * 45)
        cx, cy = 128 + 62 * math.cos(a), 128 + 62 * math.sin(a)
        d.ellipse((cx - 16, cy - 16, cx + 16, cy + 16), fill=GOLD, outline=EDGE, width=4)
    d.ellipse((104, 104, 152, 152), fill=GOLD, outline=EDGE, width=5)
icons["weapon_wheel"] = draw(wheel)

def parachute(d):
    d.pieslice((52, 52, 204, 172), 180, 360, fill=GOLD, outline=EDGE, width=6)
    for x in (64, 128, 192):
        d.line((x, 112, 128, 188), fill=EDGE, width=6)
    d.ellipse((116, 180, 140, 204), fill=GOLD, outline=EDGE, width=4)
icons["deploy"] = draw(parachute)

def detach(d):
    for ang in (45, -45):
        r = Image.new("RGBA", (S, S), (0, 0, 0, 0)); rd = ImageDraw.Draw(r)
        rd.rounded_rectangle((116, 48, 140, 208), radius=6, fill=GOLD, outline=EDGE, width=5)
        d.bitmap((0, 0), r.rotate(ang).split()[3], fill=None)
    for ang in (45, -45):
        pass
def x_mark():
    im = plate()
    for ang in (45, -45):
        r = Image.new("RGBA", (S, S), (0, 0, 0, 0)); rd = ImageDraw.Draw(r)
        rd.rounded_rectangle((114, 48, 142, 208), radius=8, fill=GOLD, outline=EDGE, width=6)
        im.alpha_composite(r.rotate(ang, resample=Image.BICUBIC))
    return im
icons["detach"] = x_mark()

def smoke(d):
    for (x, y, r) in [(100, 150, 34), (146, 136, 40), (124, 98, 30), (168, 96, 22)]:
        d.ellipse((x - r, y - r, x + r, y + r), fill=GOLD, outline=EDGE, width=5)
icons["smoke"] = draw(smoke)

def heli_action(d):
    rect(d, (60, 76, 196, 92), 4, 4)
    d.ellipse((86, 104, 170, 168), fill=GOLD, outline=EDGE, width=6)
    rect(d, (166, 124, 210, 138), 4, 4); rect(d, (120, 90, 136, 106), 3, 2)
icons["heli_action"] = draw(heli_action)

def skip(d):
    poly(d, tri(96, 128, 40, 0), 5); poly(d, tri(146, 128, 40, 0), 5)
    rect(d, (176, 90, 194, 166), 5, 3)
icons["skip"] = draw(skip)

def menu_back(d):
    d.arc((70, 74, 186, 190), 270, 90 + 360, fill=EDGE, width=1)
icons["menu_back"] = load("tank_left.png")
icons["menu_accept"] = load("drive-toggle.png")

def more(d):
    for x in (86, 128, 170):
        d.ellipse((x - 16, 112, x + 16, 144), fill=GOLD, outline=EDGE, width=5)
icons["more"] = draw(more)

def settings(d):
    for k in range(8):
        a = math.radians(k * 45)
        d.polygon([(128 + 56 * math.cos(a - 0.25), 128 + 56 * math.sin(a - 0.25)),
                   (128 + 80 * math.cos(a - 0.15), 128 + 80 * math.sin(a - 0.15)),
                   (128 + 80 * math.cos(a + 0.15), 128 + 80 * math.sin(a + 0.15)),
                   (128 + 56 * math.cos(a + 0.25), 128 + 56 * math.sin(a + 0.25))], fill=GOLD, outline=EDGE)
    d.ellipse((66, 66, 190, 190), fill=GOLD, outline=EDGE, width=6)
    d.ellipse((106, 106, 150, 150), fill=EDGE)
icons["settings"] = draw(settings)

for name, im in icons.items():
    im.resize((192, 192), Image.LANCZOS).save(os.path.join(OUT, name + ".png"), optimize=True)
print(len(icons), "icons ->", OUT)
