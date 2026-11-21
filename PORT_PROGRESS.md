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

## Этап 2.1 — приложение Fallout Droid на нативном движке (2026-11-21)

### Решения пользователя
- Переносится всё из Fallout Droid v1.1.85, кроме экрана первоначальной загрузки ресурсов и
  всего, что связано с Proton (RootFS, FEX, Wine/Proton, DXVK, Turnip, PulseAudio, vkbridge,
  Wayland, Termux, DroidDeck-сессии).
- FNV и FO3 запускаются **отдельно**, без TTW.
- Управление переносится полностью: экранные кнопки, мышь, геймпад, клавиатура.

### Собрано
- `nvport/android/` — Gradle-проект (AGP 8.13.2, compileSdk 36, minSdk 29, arm64-v8a),
  applicationId `com.rommy.falloutdroid.nativeport` (пока Proton-версия стоит рядом).
  - Java-код `com.wastelanddroid.*` из `classes4/6/7/8.dex` 1.1.85 (jadx), очищен от
    артефактов декомпиляции (`this.f$0`, константы чужих библиотек, `throws` в override,
    `final`-счётчики) и от Proton: удалены `ProtonPrefix`, `Registry`, `RuntimeManager`,
    `ContainerActivity`, `ExePatcher`, `IniTweaks`, мёртвый `ControlsOverlay`.
  - `engine/FalloutEngine` — запуск NVForever: аргументы в `filesDir/nvport_args.txt`,
    домашняя папка движка на игру (`home_fnv`, `home_fo3`), порядок плагинов из
    `Data/plugins.txt` (пишет Pip-Boy менеджер модов), распаковка схем движка из APK.
  - `engine/NativeGameActivity` — GameActivity + `libnvforever.so`, оверлей кнопок,
    физическая мышь (захват указателя → обзор), клавиатура, геймпад.
  - `input/*` — экранные кнопки в формате раскладок 1.x (`ScreenKeyStore` совместим с
    `savelayout_` из HTML-редактора), стик на 8 секторов, крестовина, переключатели,
    режимы мыши direct/trackpad/auto, редактор (`ControlsEditorActivity`).
  - `runtime/InputBridge` — VK-коды твоего `GamepadInput` → evdev → движок.
  - Ресурсы восстановлены из APK (иконки `ic_*`, `fallout.ttf`, тема `FdTheme`, mipmap).
  - Ассеты: ui (Pip-Boy, без экрана загрузки рантайма), art, audio, fonts, loading,
    pipboy_icons, controls, screenkeys, trailers, splash; `engine/schemas` (NVForever);
    лицензия GPL-3.0 движка.
- `nvport/patches/nvforever/0001-android-cdylib-entry.patch` — движок как `cdylib`:
  `android_main` (аргументы из файла, `env:KEY=VALUE`, лог в `externalFilesDir/nvforever.log`),
  OpenSSL vendored, модуль `android_input.rs` (JNI `NativeInput.key/mouseMove/cursorTo/scroll`
  → стандартные Bevy-события ввода).
- `nvport/build_engine.sh` — сборка `libnvforever.so` (NDK r29, API 29, GameActivity).
- `nvport/build_apk.sh` — движок → Gradle release → zipalign → apksigner v1+v2+v3 ключом
  Fallout Droid → проверка.

### Известные ограничения
- **Сохранения**: NVForever пишет свой формат `.nvfsave`, `.fos` оригинала не читаются.
- **Fallout 3 отдельно**: NVForever требует `FalloutNV.esm` — профиль FO3 в движке ещё не
  сделан (задача следующих этапов). Кнопка «Играть» FO3 передаёт `--world Wasteland`, но
  движок пока завершится с ошибкой проверки установки.
- NVSE/JIP/JohnnyGuitar/kNVSE как DLL не нужны: функции расширителей встроены в движок.
- **Лицензия**: движок GPL-3.0-or-later → APK с ним распространяется на условиях GPL,
  исходники патчей лежат в этом репозитории.
- Проверки на устройстве ещё не было (adb отсюда недоступен).

### Сборка 2.0.0-alpha1 (versionCode 300)
- `libnvforever.so` (GameActivity, release, strip-all): 164 МБ; экспортирует `GameActivity_onCreate`,
  `android_main`, `Java_com_wastelanddroid_engine_NativeInput_{key,mouseMove,cursorTo,scroll}`;
  зависимости только системные (`libaaudio`, `libandroid`, `liblog`, `libc`, `libm`, `libdl`).
- APK 107 МБ, подписи v1+v2+v3 ключом Fallout Droid (`apksigner verify` OK), `lintVitalRelease` OK.
- На устройстве **не проверено**: в контейнере нет KVM/adb.

### Сборка 2.0.0-alpha2 (versionCode 301) — «Играть» ничего не запускало
Причина: `GameActivity` (games-activity 4.4.2) наследует `AppCompatActivity`, а у
`NativeGameActivity` была тема на базе `Theme.Material` → `IllegalStateException`
(«You need to use a Theme.AppCompat theme») в `onCreate`, процесс падал вместе с лаунчером.
Исправления:
- `FdGameTheme` → `Theme.AppCompat.NoActionBar` (полноэкранная, чёрный фон, вырез экрана).
- Игра в отдельном процессе `:game`: падение движка не роняет лаунчер; после выхода процесс
  завершается (состояние Bevy/OnceLock не переживает повторный запуск).
- Движок: stdout/stderr → `nvforever.log` **и** logcat (тег `NVForever`), `RUST_BACKTRACE=1`,
  `catch_unwind` вокруг `main`, итог в `nvforever.exit` (`running`/`ok`/`error`/`panic`);
  ошибка командной строки больше не вызывает `exit()` процесса.
- Лаунчер: после возврата из игры читает `nvforever.exit` и показывает «Игра закрылась»
  с причиной и концом лога; «Поделиться логами» отдаёт `fd_report.txt` (устройство, итог,
  командная строка, `nvforever.log`, журнал запуска движка `home_*/logs`, crash-отчёт).
- `build_engine.sh`: исходники NVForever по умолчанию в `../../refs` (а не `$HOME/refs`),
  клон фиксируется на `a3e5406`, неприменившийся патч — ошибка, а не тихий пропуск.

### Следующий шаг
Проверка на vivo I2508: лаунчер → выбор папки FNV → «Играть» → лог движка
(`Android/data/com.rommy.falloutdroid.nativeport/files/nvforever.log`, кнопка «Поделиться логами»).
Затем: мобильный профиль рендера, графические настройки → аргументы движка, профиль Fallout 3.
