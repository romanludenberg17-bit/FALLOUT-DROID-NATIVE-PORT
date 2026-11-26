# GTA IV Native (com.rommy.gtaiv)

**Оформление** — оболочка 4NMP v502 (`shell/4NMP-v502-ui-only.apk`: лаунчер, дизайн,
музыка меню, MP-хаб, редактор раскладки, иконки). **Движок** — gta4-recomp из
LibertyRecompAndroid (статическая рекомпиляция в C++, Vulkan, без эмулятора/Wine/Box64).
Итоговый APK собирает `build_from_4nmp.py`: dex/ресурсы/assets 4NMP + наши `.so`.
Upstream: vaduur/LibertyRecompAndroid @ f4f6d8a + `patches/gtaiv-native-android.patch`.

## Требования к игре
Нужна **своя** копия **Xbox 360-версии** GTA IV (диск/ISO + title update) — рекомпиляция
сделана из Xbox 360 `default.xex`. PC-версия 1.0.8.0 этим движком **не поддерживается**.
Ресурсы Rockstar в APK не входят. Установка игры — из лаунчера (кнопка установки), см.
upstream `docs/android/INSTALL.md`.

## Установка
1. Установить `GTAIV-Native.apk` (Android 10+, arm64, ARMv8.2).
2. В лаунчере выбрать ISO/папку игры и title update, дождаться установки (~7 ГБ).
3. Play.

## Кооп без сервера (LAN / System Link)
Включён по умолчанию (`--gta4_multiplayer_backend=lan`). Оба телефона в одной Wi-Fi сети
**или** один раздаёт точку доступа, второй подключается. В игре: телефон → Multiplayer →
режим (в т.ч. кооп-миссии) → хост создаёт, второй находит сессию. Обнаружение — multicast
239.255.42.99:3074, данные — UDP напрямую. Имя игрока: `--gta4_player_name=` в `args.txt`.

## Сборка
- Только APK: открыть `android-project/` в Android Studio (готовые `.so` в `jniLibs`) → Build APK.
- Полностью из исходников: `ANDROID_HOME=... ./build_linux.sh` или workflow `GTA IV Native APK`.

## Что поменяно в оболочке 4NMP (build_from_4nmp.py)
- «ИГРАТЬ» снова запускает игру (вместо тоста «Движок вырезан» — `mPlayBtn.performClick()`).
- Пакет `com.rommy.gtaiv`, название «GTA IV Native», разрешения Wi-Fi/multicast.
- MP-хаб `shell/nmp_mp_hub.html`: рабочие вкладки Мультиплеер / Co-op / Серверы / Друзья.
- Профили `default_args*.txt` заменены на профили движка (качество консоли + LAN).

## Папка игры (как в 4NMP)
В лаунчере выбрать папку `game/` с `default.xex`, `default.xexp`, `game.iso` (можно
русифицированный), `aes_key.bin`. Установка не нужна: `default.xex`/`xexp` берутся из
папки, всё остальное — прямо из `game.iso`. Нужен доступ «Все файлы».

## Изменения движка относительно upstream
- `--game_data_root` от лаунчера учитывается (если `default.xex`/`xexp` проходят проверку);
  `game.iso` монтируется за папкой (VFS ищет дальше, если файла нет в папке).
- LAN-обнаружение на UDP 3076 (мост 4NMP пересылает на 3076, игровой трафик — 3074).
- JNI для оболочки: `TouchSettings` (редактор раскладки 4NMP) поверх нашей раскладки —
  редактор и игровой оверлей используют один `touch-controls.ini` и одну нумерацию;
  `LibertyActivity.nativeCheckpointGpuCache/nativeSetGameHapticsAvailable`; MulticastLock.
- LAN-кадры дублируются в мост 4NMP `127.0.0.1:3075` (вход по IP из MP-хаба).
- LAN-мультиплеер на Android: исправлено определение локального IP (на Android
  `gethostname()` = localhost → сессия объявлялась на 127.0.0.1), multicast привязан к
  Wi-Fi/hotspot-интерфейсу, MulticastLock, разрешения Wi-Fi, LAN по умолчанию.
- Профиль `default_args_mali.txt` для MediaTek/Mali/Tensor (выбирается автоматически при
  первом запуске): только оптимизации без изменения картинки (merge/skip invisible draws,
  lazy resolves, pipeline prewarm, async-retire, pinning потоков); графика как на консоли.
- Иконки сенсорного управления (из 4NMP v502) для контекстного touch-оверлея.
- Пакет `com.rommy.gtaiv`, имя «GTA IV Native», сборка на Linux (без Git Bash/cygpath).
