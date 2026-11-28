#!/usr/bin/env python3
"""GTA IV Native on the full 4NMP v504 build (its engine + UI, 60-80 FPS) with:
  - libq.so LAN shim (shim/): the engine's own LAN co-op advertised 127.0.0.1;
    libmain.so's DT_NEEDED "libz.so" is renamed "libq.so" (libq depends on libz)
  - working MP / co-op hub (shell/nmp_mp_hub.html), bridge -> UDP 3076
  - package com.rommy.gtaiv, label, Wi-Fi multicast permissions, version
  - LAN discovery port 3076 + a thermal / FPS mini patch in the launcher's profiles
  - 4NMP main menu: launcher/ (NmpHomeActivity, classes3.dex) + shell/home/ (HTML)
usage: build_from_4nmp_v504.py <4NMP-v504.apk> <libq.so> <out unsigned.apk>
"""
import glob, hashlib, os, shutil, struct, subprocess, sys, tempfile, zipfile, zlib
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_from_4nmp as base

base.VERSION_CODE = 637
base.VERSION_NAME = "1.3.7"
base.LABEL = "4NMP"  # the project's name
HERE = os.path.dirname(os.path.abspath(__file__))

def patch_dex(dex):
    d = bytearray(dex)
    for old, new in ((bytes.fromhex("1306020c"), bytes.fromhex("1306040c")),   # bridge -> 3076
                     (b"nmp_v504_clean", b"nmp_v637_clean"),                  # args.txt reset once (Mali profile fixes)
                     # writeCoopArgs: skipping the intro left the game on a black screen
                     # (and the line stayed in args.txt for every later start)
                     (b"\n--gta4_skip_intro=true\n", b"\n#-gta4_skip_intro=true\n")):
        assert d.count(old) == 1, old
        j = d.find(old); d[j:j + len(old)] = new
    d[12:32] = hashlib.sha1(bytes(d[32:])).digest()
    struct.pack_into("<I", d, 8, zlib.adler32(bytes(d[12:])) & 0xFFFFFFFF)
    return bytes(d)

# BuildContextTouchLayout's compact(action, label, buttons, trigger, visible=w5, ...)
# calls: force visible=false for buttons the mobile layout does not need.
HIDDEN_BUTTONS = {
    0x1654b60: ("TURBO (qte_turbo)", bytes.fromhex("05010012")),       # and  w5, w8, #0x1
    0x1654d98: ("ENGINE OFF (engine_off)", bytes.fromhex("e5079f1a")), # cset w5, ne
}
MOV_W5_WZR = bytes.fromhex("e5031f2a")

def patch_libmain(so):
    d = bytearray(so)
    for addr, (what, expected) in HIDDEN_BUTTONS.items():  # .text: file offset == vaddr
        assert bytes(d[addr:addr + 4]) == expected, f"unexpected code for {what}"
        d[addr:addr + 4] = MOV_W5_WZR
    old = b"\0libz.so\0"
    assert d.count(old) == 1, "libz.so NEEDED string not unique"
    j = d.find(old); d[j:j + len(old)] = b"\0libq.so\0"
    return bytes(d)

# Thermal / FPS mini patch for the high-end (Adreno) profile. Only switches the
# engine already has; the redundancy-elimination ones are those the 4NMP author
# ships in the Mali / Helio G99 profiles. Rendering quality is unchanged.
MINI_PATCH = [
    "# --- GTA IV Native thermal / FPS mini patch ---",
    "# 60 FPS cap (half of a 120 Hz panel, even pacing): no GPU time burnt above 60.",
    "--gta4_frame_limit=60",
    "# Under Android thermal pressure lower the draw distance instead of letting the SoC throttle.",
    "--android_thermal_guard=true",
    "# Skip work that does not change the picture.",
    "--gta4_native_skip_invisible_draws=true",
    "--gta4_native_dedupe_vertex_bindings=true",
    "--gta4_native_keep_draw_state=true",
    "--gta4_native_skip_repeated_clears=true",
    "--gta4_native_lazy_resolves=true",
    "--gta4_native_lossless_resolve_optimization=true",
    "--gta4_native_water_reflection_on_demand=true",
    "# Environment cube map every 2nd frame (the 4NMP profiles use 3-4).",
    "--gta4_native_environment_reflection_interval=2",
]

# Mali / Helio G99 profiles. Physics runs on the CPU and is fine on Adreno, so
# the CPU-side switches these profiles alone changed go back to the Adreno
# profile's values; the renderer becomes "native" (gta4-mobile) and shaders
# keep FP32 (half precision broke the shadow-map depth compare).
MALI_DROP = ("--protect_zero=", "--gta4_fps_guard=", "--gta4_model_lod_distance_scale=",
             "--gta4_native_fs_relaxed_precision=", "--gta4_shadow_map_base_size=", "--gpu_plugin=")
MALI_PATCH = [
    "# --- 4NMP Mali / G99 fixes ---",
    "# Native renderer (launcher: Rendering -> Renderer -> Native).",
    "--gpu_plugin=gta4-mobile",
    "# Physics: same CPU-side setup as the Adreno profile (no writable zero page,",
    "# no 30 FPS guard cuts), stabilized physics steps on frame-time spikes.",
    "--gta4_fps_guard=0",
    "--gta4_clamp_physics_timestep=true",
    "# World and its collision stream in farther: no falling through the map at speed.",
    "--gta4_model_lod_distance_scale=0.80",
    "# Shadows: FP32 depth compare and a 256 shadow map (no blocky shadow acne).",
    "--gta4_native_fs_relaxed_precision=false",
    "--gta4_shadow_map_base_size=256",
]

def patch_args(text, name):
    lines = text.splitlines()
    extra = ["", "--gta4_lan_discovery_port=3076"]
    if name == "assets/default_args.txt":  # Adreno; the Mali / G99 profiles are tuned already
        keys = {l.split("=")[0] for l in MINI_PATCH if l.startswith("--")}
        lines = [l for l in lines if l.split("=")[0] not in keys]
        extra += [""] + MINI_PATCH
    elif name in ("assets/default_args_g99.txt", "assets/default_args_mali.txt"):
        lines = [l for l in lines if not l.startswith(MALI_DROP)]
        extra += [""] + MALI_PATCH
    return "\n".join(lines + extra + [""]).encode()

SDK = os.environ.get("ANDROID_SDK_ROOT", "/opt/android-sdk")
LAUNCHER_ACTIVITY = ("com.rommy.NmpLauncherActivity", "com.rommy.NmpHomeActivity")

def build_launcher_dex():
    """launcher/src -> classes3.dex (javac against android.jar + a stub of the 4NMP launcher)."""
    android_jar = sorted(glob.glob(os.path.join(SDK, "platforms", "android-*", "android.jar")))[-1]
    d8 = sorted(glob.glob(os.path.join(SDK, "build-tools", "*", "d8")))[-1]
    src = os.path.join(HERE, "launcher")
    with tempfile.TemporaryDirectory() as tmp:
        stubs, cls, out = (os.path.join(tmp, x) for x in ("stubs", "cls", "dex"))
        for d in (stubs, cls, out): os.makedirs(d)
        javac = ["javac", "-nowarn", "-Xlint:-options", "-source", "8", "-target", "8", "-bootclasspath", android_jar]
        subprocess.run(javac + ["-d", stubs] + glob.glob(os.path.join(src, "stubs", "**", "*.java"), recursive=True), check=True)
        subprocess.run(javac + ["-cp", stubs, "-d", cls] + glob.glob(os.path.join(src, "src", "**", "*.java"), recursive=True), check=True)
        classes = glob.glob(os.path.join(cls, "**", "*.class"), recursive=True)
        subprocess.run([d8, "--release", "--min-api", "29", "--lib", android_jar, "--classpath", stubs,
                        "--output", out] + classes, check=True)
        return open(os.path.join(out, "classes.dex"), "rb").read()

def rename_activity(axml):
    """The launcher activity becomes NmpHomeActivity (a subclass of the 4NMP launcher)."""
    buf = bytes(axml)
    strings, utf8, flags, pool_size = base.read_pool(buf, 8)
    old, new = LAUNCHER_ACTIVITY
    assert strings.count(old) == 1, "launcher activity not found in the manifest"
    strings[strings.index(old)] = new
    pool = base.write_pool(strings, utf8, flags)
    rest = buf[8 + pool_size:]
    return struct.pack("<HHI", 0x0003, 8, 8 + len(pool) + len(rest)) + pool + rest

def main():
    src, libq, out = sys.argv[1:4]
    with zipfile.ZipFile(src) as zin, zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            n = item.filename
            if n.startswith("META-INF/") and n.split("/")[-1].upper().endswith((".SF", ".RSA", ".MF", ".EC", ".DSA")):
                continue
            data = zin.read(n)
            if n == "AndroidManifest.xml": data = rename_activity(base.patch_manifest(data))
            elif n == "resources.arsc": data = base.patch_arsc(data)
            elif n == "classes2.dex": data = patch_dex(data)
            elif n == "lib/arm64-v8a/libmain.so": data = patch_libmain(data)
            elif n.startswith("assets/Resources/touch-icons/") and os.path.isfile(
                    os.path.join(HERE, "touch-icons-v504", os.path.basename(n))):
                data = open(os.path.join(HERE, "touch-icons-v504", os.path.basename(n)), "rb").read()
            elif n == "assets/nmp_mp_hub.html":
                data = open(os.path.join(HERE, "shell", "nmp_mp_hub.html"), "rb").read()
            elif n.startswith("assets/default_args") and n.endswith(".txt"):
                data = patch_args(data.decode(), n)
            store = n == "resources.arsc" or n.endswith((".png", ".jpg", ".mp3", ".zip"))
            zout.writestr(zipfile.ZipInfo(n, item.date_time), data,
                          zipfile.ZIP_STORED if store else zipfile.ZIP_DEFLATED)
        zout.write(libq, "lib/arm64-v8a/libq.so")
        assert "classes3.dex" not in zin.namelist()
        zout.writestr("classes3.dex", build_launcher_dex())
        drivers = os.path.join(HERE, "drivers")  # bundled Vulkan drivers (NmpHomeActivity installs them)
        for path in sorted(glob.glob(os.path.join(drivers, "**", "*"), recursive=True)):
            if os.path.isfile(path):
                zout.write(path, "assets/drivers/" + os.path.relpath(path, drivers).replace(os.sep, "/"))
        home = os.path.join(HERE, "shell", "home")
        for path in sorted(glob.glob(os.path.join(home, "**", "*"), recursive=True)):
            if os.path.isfile(path):
                name = "assets/home/" + os.path.relpath(path, home).replace(os.sep, "/")
                stored = path.endswith((".png", ".jpg", ".woff2"))
                zout.write(path, name, zipfile.ZIP_STORED if stored else zipfile.ZIP_DEFLATED)
    print("wrote", out)

if __name__ == "__main__":
    main()
