# Fallout Droid Native — CHANGELOG

Only changes that are in the code. "Not verified on a device" marks what was built and
checked statically but not yet confirmed by a tester.

## 2.0.0-alpha41 (340)
- Console `fxtest`: three test cards before the camera (opaque red, blended green, added
  blue) to find why blended/added effects (muzzle flashes, explosions, blood, decals) are
  invisible on phones. alpha38's census showed those effects are queued for drawing.
- Console `fdland off|on`: a child worldspace takes no land from its parent (from the next
  start) — the experiment for FO3 Megaton's slabs; the run log lists the squares that took
  the parent's land.

## 2.0.0-alpha40 (339)
- Game settings: Resolution (360p–1080p, native), applied at once.
- Texture Quality: High, Medium, Low, Ultra Low, Potato (applied at the next start).
- Phone Preset: Ultra Low / Potato (textures, no shadows, reflections, water effects,
  grass, glow; draw distances kept).

## 2.0.0-alpha39 (338)
- Console `fdtest`: a test character (weapons, armour, ammo, perks, S.P.E.C.I.A.L. 10,
  skills 100, the dog, a trip to the middle of the map). Not verified on a device.
- Galaxy S25 Ultra profile without the desktop effects (a freeze on going outdoors).

## 2.0.0-alpha38 (337)
- Run log: transparent-phase census every 10 s.

## 2.0.0-alpha37 (336)
- A repeated lost GPU device after a start-over retries once with a light picture for that
  start only; no restart loop, the player's settings untouched.

## 2.0.0-alpha36 (335)
- GPU uploads always limited on phones (64 MB/frame loading, 16 MB playing).
- Late meshes retried up to ~70 s (Megaton walls missing on Mali).
- Phones never hold shadow cascades; shadow resolution, terrain and fine shadows hidden.
- Loading overlay removed after its draw (ContentFrameLayout crash).
- Cloud shadows on by default on strong chips; low class: CAS sharpening, no FXAA.

## 2.0.0-alpha35 (334)
- mimalloc built with local-dynamic TLS (alpha33/34 did not start); the build refuses a
  STATIC_TLS engine.

## 2.0.0-alpha33 / 34
- VATS part highlight drawn in the world pass on phones (no mask camera).
- Sun light turns in game-minute steps on phones (stable shadow texel grid).
- Decal quad with tangents; Pip-Boy radio state saved; Rust heap on mimalloc.
- Strong chips: full graphics rows, no automatic downgrade after a crash; Texture Quality.
- S25 Ultra profile; body-texture warning for body replacers.

## 2.0.0-alpha29 – alpha32
- Mali auto-exposure alignment guard; Scudo spare heap; console text bar; save over slots.
- ETC2 transcoding on GPUs without BC; frame log; big-core threads + ADPF.
- FOMOD installer, .7z mods, Data root at any depth, replacer gating.
- Automatic report upload (needs a token); low class 60 FPS cap and 120 Hz display mode.

## Open (handoff)
- Blended/added effects invisible (decide from `fxtest`).
- FO3 Megaton land (decide from `fdland off`), missing walls/collision.
- Shadow flicker that remains, cloud shadows on characters.
- FO3 iron sights (no sighting node in FO3 weapon models).
- Repeating GPU device loss a few seconds after a load on Snapdragon 8 Gen 5.
- Mali/Xclipse profiling from tester frame logs.
