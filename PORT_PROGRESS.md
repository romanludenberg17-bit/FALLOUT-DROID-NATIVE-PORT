# PORT_PROGRESS — нативный порт Fallout: New Vegas на Android (arm64-v8a)

Новый порт на базе открытого движка, отдельно от обёртки `libfnv.so` (Fallout Droid NV Native 1.3.x).
Работа идёт итерациями, каждая заканчивается записью в этот файл.

## Цель (уточнено 2026-11-21)
Fallout Droid без Proton/Wine/FEX: одно Android-приложение, которое **нативно** запускает
Fallout: New Vegas и Fallout 3, с дизайном Fallout Droid v1.1.85.

### Что берём из Fallout Droid v1.1.85 (`com.rommy.falloutdroid`)
- Pip-Boy лаунчер: `assets/ui/index.html` (385 КБ HTML/CSS/JS, WebView), шрифты
  (FalloutGothic, Monofonto), `art/`, `loading/`, `pipboy_icons/`, `audio/` (ui_click,
  city_of_ruin), трейлеры, `screenkeys/` и `controls/` (раскладки сенсорного управления).
- Мост `falloutpicker://...` (play_, verify_fnv, folder_, setpath_, quality_*, res_*, mods_,
  touch_cfg_, gamepad_editor, share_logs и др.) — переписываем на Kotlin/Java как
  `@JavascriptInterface`, без hand-made DEX.
- Не берём: linuxfs (Arch RootFS), FEX, Proton, DXVK, Turnip, PulseAudio, vkbridge, wayland,
  Termux — всё это заменяет нативный движок.

### Движок и Fallout 3
- NVForever требует `FalloutNV.esm` (`crates/fnv-install`): Fallout 3 сейчас идёт только
  через TTW (обе игры + установщик TTW). Самостоятельный запуск `Fallout3.esm` — отдельная
  доработка движка (различия записей ESM 0.94/1.34, игровые правила FO3).
- OpenNV: FO3 — «bounded Vault 101 opening work», кампания не полная.
- Вывод: FNV — первым; FO3 — сначала через TTW-режим, затем самостоятельный профиль FO3.

## Этап 1 — аудит окружения и выбор базы (2026-11-21)

### Окружение (облачный контейнер этой сессии)
| Инструмент | Состояние |
|---|---|
| Rust | 1.100.0 + target `aarch64-linux-android` (установлен) |
| CMake / Ninja / clang | 3.28.3 / 1.11.1 / 18.1.3 (хостовый, без Android sysroot) |
| JDK / Gradle | 21 / 8.14.3 |
| **Android SDK, NDK, adb** | **нет**; `dl.google.com` заблокирован сетевой политикой |
| .NET SDK / Godot | нет; установщики .NET и релизы Godot/GitHub заблокированы |
| crates.io, maven.google.com, Maven Central, NuGet, static.rust-lang.org | доступны |
| Устройство для `adb install` / `logcat` | не подключено (облачный контейнер, USB нет) |
| `./game_data` | отсутствует (файлы игры только на устройстве пользователя) |

### Разбор кандидатов
| Проект | Стек | Состояние | Android | Вывод |
|---|---|---|---|---|
| **NVForever** (`a3e5406`) | Rust, Bevy 0.20 + wgpu (Vulkan/GLES), rodio/cpal (AAudio) | ~730 тыс. строк; бой, ИИ, диалоги, меню, аудио (по `docs/status.md`: бой/ИИ/диалоги «Done», меню и аудио «Partly») | Нет готового таргета, но Bevy/winit/wgpu поддерживают Android; Windows-код закрыт `cfg(windows)` | **Выбран** |
| OpenNV (`e699885`) | C#, Godot 4.7, Forward+ | Самый живой маршрут FNV (ESM/BSA, скрипты, диалоги, инвентарь), но кампания не полная; 1569 .cs | Нет экспорта под Android | Запасной вариант |
| OpenNevada-Android (`7fb55d6`) | форк OpenNV | Отстаёт от OpenNV (753 .cs против 1569). В `export_presets.cfg` только «Windows Desktop», рендер Forward+, NativeAOT не настроен | Android-сборки в репозитории фактически нет | Не используем |
| OpenMW / OpenMW-Android | C++, OSG, GLES/GL4ES | Движок Morrowind; `components/esm4`, `bsa`, `nif` — справочник по форматам | Готовая Android-сборка, но для Morrowind | Справочник |
| fnv_decomp | C++ пересборка exe 1.4.0.525 | Сверка логики (аудио-менеджер, формулы) | — | Справочник |
| Steamless | снятие Steam DRM | Не нужен: движки читают ESM/BSA, а не exe | — | Не используем |

### Почему NVForever
1. Единый нативный тулчейн (Rust + NDK), без .NET/Godot, которые в этом окружении не скачать.
2. Наибольший объём готовой игровой логики, включая звук через cpal → AAudio (то, что не работает в текущем порте).
3. Проверено: слой данных (`fnv-esm`, `fnv-bsa`, `fnv-nif`, `fnv-dds`, `fnv-vfs`, `fnv-world`, `fnv-script`, `fnv-save`) **компилируется под `aarch64-linux-android` без изменений**.
4. Движок (`nvforever` + Bevy) доходит до C-зависимостей (`blake3` и др.) и останавливается без NDK. С `bevy/android-native-activity` ошибок Rust-кода до этого места нет.

### Риски
- Рендер Bevy настроен под десктоп (Forward+ clustered PBR, `raw_vulkan_init`, DLSS) → нужен мобильный профиль: отключить DLSS/кластеры/тени высокого качества, GLES-фолбэк для Mali без Vulkan 1.1.
- Пути настроек под Windows (`Documents\My Games\NVForever`) → перенести в `getExternalFilesDir`.
- Объём: Bevy-сборка тяжёлая (APK ~80–150 МБ, первая сборка ~20–40 мин на 4 ядрах).

### Собрано
- `nvport/stage1_check.sh` — аудит инструментов, клон NVForever на зафиксированном коммите, проверка слоя данных под Android (проходит), проверка всего движка при наличии NDK.

### Блокеры для Этапа 2
1. Доступ к `dl.google.com` (Android SDK cmdline-tools, NDK r27+, platform-tools) — нужен для сборки APK.
2. Нет устройства: `adb install`/`logcat` из облака невозможны. Варианты: пользователь ставит APK сам и присылает logcat, либо подключает свой компьютер через Remote Control.
3. Файлы игры для проверки загрузки — только на устройстве пользователя.

### Следующий шаг (после согласования)
Этап 2.1: установить SDK/NDK, добавить crate `nvf-android` (cdylib + `#[bevy_main]`, NativeActivity → позже GameActivity), собрать тестовый APK, который открывает окно, находит `FalloutNV.esm`/BSA в выбранной папке и пишет результат разбора в logcat.
