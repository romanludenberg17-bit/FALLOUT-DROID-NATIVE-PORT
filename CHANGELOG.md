# Fallout Droid Native — CHANGELOG

## 2.0.0-alpha47 (346)
Built; unit tests of the new texture shrink and GPU family code pass. Nothing verified on a
device: no Android phone or game data here.
- Texture Quality is never applied while playing: the live re-read of alpha45-46 is removed
  (module `texture_live.rs` and nvf-assets' `set_mip_skip`/`held`/`reread` deleted). A choice
  is only kept (`texture_quality.txt`); the row shows "… (after restart)" until the next
  start, which reads every texture at that quality before the world's are loaded.
- Very Low and Potato also shrink DXT textures stored without smaller levels (they had none
  to skip and stayed full size): decoded, box-filtered by the dropped levels (at least 8
  texels a side), with a mip chain, as RGBA8 (a 1024² DXT1 of 512 KB becomes 32² at Potato).
  Interface, HUD, Pip-Boy, fonts and distant LOD keep every level as before.
- Lost GPU device (Adreno 829, both games; Fallout 3 every time ~3 s after its main menu
  opens over Vault101d, also in alpha8 already): the driver gives no fault data
  (VK_EXT_device_fault: 0 records). Experiment: Bevy's GPU mesh preprocessing/culling
  (compute + indirect draws) is off on Adreno 8xx phones, the CPU path Bevy itself uses on
  Adreno 730 and older; logged `gpu preprocessing: …`; console `fdgpu none|preprocess|culling|auto`
  chooses for the next start. Not verified.
- The "pipeline … could not be loaded" errors of alpha46 were the shadow census's own
  mistake: a pipeline waiting for its shader (Bevy compiles it once the shader is in) was
  counted as failed. Only real failures are logged now.
- Graphics profiles: the launcher wrote distances (1.000), light shafts, AO, water effects,
  water reflections, terrain and haze rows into NVForever.ini at every start, over the
  game's own choices (a Potato profile read Custom after a restart and drew everything).
  They are first values only now; the game's settings own them (the GPU safe mode still
  forces its values).
- Thermal Adaptive Mode (Normal, Warm, Hot, Critical) from Android's thermal status and
  headroom: the dynamic resolution's top falls to 85/75/65 % of the chosen resolution and
  comes back as the phone cools; `thermal:` lines in the run log, the mode in every frame
  log line; hitch lines name the pipelines compiling at the time.

## 2.0.0-alpha46 (345)
Built and checked by compilation only; nothing here verified on a device yet.
- GPU detection from the Vulkan device (not the phone's name): run log `gpu:` lines with the
  GPU name, PCI vendor id and family (Adreno, Mali, Xclipse, PowerVR; an unknown GPU stays
  "other"), driver, Vulkan API version, enabled extensions, memory heaps; kept in
  `gpu_family.txt` for the launcher.
- Memory every 30 s, three sources never mixed: `memory: Vulkan budget/usage (driver,
  VK_EXT_memory_budget) | wgpu allocated/reserved (wgpu allocator report) | process RAM
  (/proc/self/statm)`, "not available" where a source gives nothing.
- A GPU buffer that cannot be mapped at creation (lost device or no GPU memory) is logged
  and goes to the device-lost handler instead of panicking ("Failed to get mapped range …
  GpuClusterableObjectIndexListsStorage" was that panic: the buffer's name was only the next
  one made, not a cause).
- An updated GPU asset (texture, material, mesh) keeps its previous version until the new one
  is uploaded: before, an update waiting for the frame's upload budget left it missing for
  those frames, and the meshes using it were not drawn (blinking).
- Sun shadows: the light turns every frame again (the game-minute steps since alpha33 moved
  every shadow by ~4 texels every ~2 s); dynamic resolution keeps the screen's exact aspect
  at every step (a rounded aspect refitted the cascades to a new texel size and moved every
  shadow edge at each step).
- Shadow diagnostics: `shadow census:` lines every 10 s (sun on/off flips, frames without
  cascades, light steps, cascades, casters; cloud image present; shader pipelines waiting or
  failed, each failure named); console `fdshadows off|sun|cloud|both` for the four tests.
- Texture Quality switching: one job at a time; a new choice drops the batch on its way and
  starts over; a texture that fails keeps its picture; over a quarter failing puts the
  previous quality back and reads it again; progress shown in the row ("Medium (120/900)").
- Graphics Profile row: Ultra, High, Medium, Low, Potato over the real rows (textures,
  resolution, dynamic resolution, sun shadows and distance, object/actor/item/tree/grass
  distances, grass, water, bloom, glow, eye adaptation, light shafts, cloud shadows, AO,
  anisotropy, anti-aliasing); Custom when any row differs; Reset to Default (this phone's
  profile by class and RAM). The stored row never re-applies at the start: rows are kept
  on their own.

## 2.0.0-alpha45 (344)
- Texture Quality in the game's settings: Ultra, High, Medium, Low, Very Low, Potato (0–5 mip
  levels dropped), applied at once without a restart: every world texture held is read again
  on a worker thread in batches of 24 and put back under its own handle; the old GPU texture
  is freed once the materials sampling it are bound again. Interface and distant-LOD textures
  keep every level. The run log reports the count, the time and the GPU size before/after
  (`texture quality: …`). Not verified on a device. The names moved one step: the former
  "High" (0) is now "Ultra".

## 2.0.0-alpha44 (343)
- Garbage geometry (stretched white/magenta triangles, the large brown sphere) fixed: alpha43's
  "no Pip-Boy warm-up under the loading screen" is reverted — without the warm-up the hidden
  Pip-Boy rig stayed in the draw phases with no skin data.
- Loading screen sends GPU uploads without a limit again (as upstream); 16 MB/frame in play.
- Lost GPU device: VK_EXT_device_fault is enabled where the driver has it, and on a loss the
  run log gets the driver's fault description, faulting addresses and vendor codes
  (`device lost: …` lines) — to find the cause instead of guessing. Not verified on a device.

## 2.0.0-alpha43 (342)
- No Pip-Boy warm-up under a phone's loading screen (its screen camera put away was followed
  by a lost GPU device ~2 s later on Adreno 8xx).
- A child worldspace with Use Land Data takes the parent's land on every square, its own
  never read, as the original does (TESWorldSpace::FindParentFile, f3_decomp) — FO3 Megaton.
- Mod installer: RAR 1.5–4 (junrar), an archive inside an archive, archives without Data
  folders refused with a message instead of being put loose in Data.

## Fallout Droid NV Classic 1.3.4 (30) — the static-recompile port
- API tracing opt-in again (/sdcard/FalloutDroidNV/trace), off by default.

## 2.0.0-alpha42 (341)
- Pipeline prewarm off on phones: every mesh of the loaded cells was drawn unculled under the
  loading screen (frames of 11 s on an 8 Elite, a 61 s screen, then a lost GPU device).
- Main pass keeps "nearer or equal" depth on phones (no "equal" test against the prepass,
  no prepass coverage): mobile shader compilers gave holes and flickering texels.
- Dynamic Resolution (game settings, on by default): 70–100 % of the chosen resolution by
  the frame time, 8 % steps every 3 s of play.

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
