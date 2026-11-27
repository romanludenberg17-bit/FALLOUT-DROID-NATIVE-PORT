#!/usr/bin/env python3
"""Maps the 'GTA4 Transparent Buttons Pack' onto the 4NMP v504 touch icon names.
usage: map_transparent_icons.py <pack dir> <out dir> [4NMP touch-icons dir]
Icons the pack has no counterpart for are left out (the APK keeps 4NMP's)."""
import os, sys
from PIL import Image

PACK, OUT = sys.argv[1], sys.argv[2]
NMP = sys.argv[3] if len(sys.argv) > 3 else None
NEW = os.path.join(PACK, "GTA4_New_Icons_PNG")
ORIG = os.path.join(PACK, "Original_Icons_PNG")
os.makedirs(OUT, exist_ok=True)

def src(name):
    for d in (NEW, ORIG):
        p = os.path.join(d, name + ".png")
        if os.path.isfile(p):
            return p
    raise FileNotFoundError(name)

MAP = {
    # on foot
    "fire": "shoot", "attack": "punch", "attack_alt": "kick", "aim": "hud_lockon", "free_aim": "aim",
    "jump_climb": "jump", "run_sprint": "sprint", "crouch": "crouch", "cover": "cover", "reload": "reload",
    "combat_roll": "dodge_block", "grenade": "hud_throw", "look_behind": "look_behind",
    "weapon_next": "weapon_next", "weapon_previous": "weapon_prev", "weapon_wheel": "weapon_switch",
    "weapon_holster": "punch", "interact_use": "interact", "interact_buy": "StoreBuy", "taxi": "taxi_hail", "taxi_passenger": "taxi_hail", "scope_zoom": "hud_plus",
    "zoom_in": "hud_plus", "zoom_out": "hud_minus", "map": "radar_zoom", "mission": "mission_start",
    # vehicles
    "accelerate": "accelerate", "brake": "brake", "handbrake": "handbrake", "parking_brake": "handbrake",
    "horn": "horn", "headlights": "headlights", "camera_cycle": "cinematic_cam", "vehicle_fire": "shoot",
    "vehicle_alt_fire": "leftshoot", "driveby_weapon": "leftshoot", "radio_next": "radio_next",
    "steer_left": "hud_left", "steer_right": "hud_right", "boat": "hud_boat", "motorcycle": "hud_bike",
    "helicopter": "hud_chopper", "plane": "hud_plane", "heli_yaw_left": "hud_tank_left",
    "heli_yaw_right": "hud_tank_right", "deploy": "hud_parachute", "detach": "hud_x",
    # phone / menus
    "phone": "phone_niko", "pause": "pause_menu", "native_start": "pause_menu", "phone_accept": "hud_check",
    "phone_back": "menu_back", "edit_done": "hud_check", "phone_left": "hud_left", "phone_right": "hud_right",
    "phone_down": "menu_down", "target_prev": "hud_left", "target_next": "hud_right",
}
DERIVED = {  # name: (source, transform)
    "radio_previous": ("radio_next", Image.FLIP_LEFT_RIGHT),
    "phone_up": ("menu_down", Image.ROTATE_180),
    "heli_descend": ("menu_down", None),
    "heli_ascend": ("menu_down", Image.ROTATE_180),
}

def save(img, name, fill=0.74):
    """Same visible size for every glyph: crop to its pixels, fit into the
    central `fill` share of the canvas (the pack's icons vary a lot)."""
    img = img.convert("RGBA")
    box = img.getchannel("A").point(lambda a: 255 if a > 24 else 0).getbbox()
    if box:
        img = img.crop(box)
    size = 192
    scale = fill * size / max(img.size)
    img = img.resize((max(1, round(img.width * scale)), max(1, round(img.height * scale))), Image.LANCZOS)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.alpha_composite(img, ((size - img.width) // 2, (size - img.height) // 2))
    canvas.save(os.path.join(OUT, name + ".png"), optimize=True)

def pack_style(mask_img, outline=9):
    """A solid white 4NMP glyph redrawn the pack's way: white fill and black
    outline, both at alpha 127."""
    from PIL import ImageFilter
    a = mask_img.convert("RGBA").getchannel("A").point(lambda v: 255 if v > 96 else 0)
    ring = a.filter(ImageFilter.MaxFilter(outline * 2 + 1))
    out = Image.new("RGBA", a.size, (0, 0, 0, 0))
    out.paste((0, 0, 0, 127), (0, 0), ring)
    out.paste((255, 255, 255, 127), (0, 0), a)
    return out

for name, s in MAP.items():
    save(Image.open(src(s)), name)
for name, (s, t) in DERIVED.items():
    im = Image.open(src(s))
    save(im.transpose(t) if t is not None else im, name)
count = len(MAP) + len(DERIVED)
if NMP:
    # One door for getting in and out of any vehicle (4NMP's door, pack style).
    door = pack_style(Image.open(os.path.join(NMP, "context.png")))
    for name in ("context", "focus_exit", "interact_leave", "vehicle_cycle"):
        save(door, name); count += 1
print(count, "icons ->", OUT)
