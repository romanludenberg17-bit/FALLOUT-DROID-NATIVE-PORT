#!/usr/bin/env python3
"""Maps the 'GTA4 Transparent Buttons Pack' onto the 4NMP v504 touch icon names.
usage: map_transparent_icons.py <pack dir> <out dir>
Icons the pack has no counterpart for are left out (the APK keeps 4NMP's)."""
import os, sys
from PIL import Image

PACK, OUT = sys.argv[1], sys.argv[2]
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
    "context": "hud_car", "combat_roll": "dodge_block", "grenade": "hud_throw", "look_behind": "look_behind",
    "weapon_next": "weapon_next", "weapon_previous": "weapon_prev", "weapon_wheel": "weapon_switch",
    "weapon_holster": "punch", "interact_use": "interact", "interact_buy": "StoreBuy", "interact_leave": "menu_back",
    "focus_exit": "menu_back", "taxi": "taxi_hail", "taxi_passenger": "taxi_hail", "scope_zoom": "hud_plus",
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

def save(img, name):
    img.convert("RGBA").resize((192, 192), Image.LANCZOS).save(os.path.join(OUT, name + ".png"), optimize=True)

for name, s in MAP.items():
    save(Image.open(src(s)), name)
for name, (s, t) in DERIVED.items():
    im = Image.open(src(s))
    save(im.transpose(t) if t is not None else im, name)
print(len(MAP) + len(DERIVED), "icons ->", OUT)
