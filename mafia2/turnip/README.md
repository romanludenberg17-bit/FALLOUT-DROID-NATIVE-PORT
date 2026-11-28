# Turnip для Adreno 8xx (ReMaf2)

На vivo I2508 (Adreno 829, Android 17) обе сборки Turnip из порта рисуют кадр игры правильно,
но на экран он выходит полосами или точечным шумом.

## Причина (по исходникам Mesa 26.3)
Turnip узнаёт раскладку буфера экрана (линейный / тайловый / сжатый UBWC) у gralloc. NDK-сборки
Turnip не могут использовать системный IMapper и берут `u_gralloc_fallback`. Тот распознаёт
только старые дескрипторы Qualcomm (метка `gmsm`), а для остальных возвращает
`DRM_FORMAT_MOD_INVALID`, то есть «раскладка неизвестна». Тогда Turnip пишет кадр в своём
формате, а система читает буфер в другом:
- без флагов — точечный шум (буфер сжатый UBWC);
- `sysmem,noubwc` — горизонтальные полосы (тайлы прочитаны как строки).

## Исправление (`0001-linear-uncompressed-swapchain.patch`)
1. `vk_android.c`: к флагам буферов экрана добавляются `SW_READ_RARELY | SW_WRITE_RARELY`
   (CPU_READ / CPU_WRITE). С ними gralloc выделяет линейный буфер без UBWC.
2. `u_gralloc_fallback.c`: «неизвестная» раскладка считается линейной (`DRM_FORMAT_MOD_LINEAR`),
   шаг строки берётся из gralloc.

Выключается переменной `REMAF2_LINEAR_WSI=0`.

## Сборка (NDK r29, Mesa 26.3.0-rc2)
```bash
curl -O https://archive.mesa3d.org/mesa-26.3.0-rc2.tar.xz && tar xf mesa-26.3.0-rc2.tar.xz
cd mesa-26.3.0-rc2 && patch -p1 < ../0001-linear-uncompressed-swapchain.patch
meson setup build --cross-file android-aarch64.cross --buildtype=release \
  -Dplatforms=android -Dplatform-sdk-version=31 -Dandroid-stub=true -Dandroid-libbacktrace=disabled \
  -Dgallium-drivers= -Dvulkan-drivers=freedreno -Dfreedreno-kmds=kgsl \
  -Degl=disabled -Dgles1=disabled -Dgles2=disabled -Dopengl=false -Dglx=disabled \
  -Dllvm=disabled -Dzstd=disabled -Dxmlconfig=disabled -Dbuild-tests=false
ninja -C build src/freedreno/vulkan/libvulkan_freedreno.so
```
Готовый `.so` кладётся в `Android/data/com.mafia2re.droid/files/driver/`, и лаунчер передаёт его
игре через `MAFIA2RE_CUSTOM_DRIVER`. Либо его можно встроить в APK как третий вариант Turnip.
