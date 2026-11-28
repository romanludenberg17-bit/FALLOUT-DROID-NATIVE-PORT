# Дополнительные Vulkan-драйверы (не в git)

`build_m2.py` кладёт каждый `*.so` отсюда в `lib/arm64-v8a/` APK.

| Файл | Что это | Откуда |
|---|---|---|
| `libvulkan_ddturnip.so` | DD-Turnip v1.0.0 (DroidDeck), Mesa 27.0.0-devel edce88a39e, Adrenotools-драйвер с IMapper 5 и Adreno 8xx | `DD-Turnip-Emu-v1.0.0.zip` → `libvulkan_freedreno.so`; исходники: github.com/Droid-Deck/Drivers/releases/tag/DD-Turnip-v1.0.0 |

Контрольные суммы — в `SHA256SUMS`. Лицензии (MIT для Mesa, GPL-3 для патчей) лежат в `assets/licenses/dd-turnip/`
и попадают в APK.
