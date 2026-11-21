package com.wastelanddroid.runtime;

import com.wastelanddroid.engine.NativeInput;
import com.wastelanddroid.input.KeyMap;

/**
 * Gamepad -> PC input adapter. Fallout Droid 1.x sent Windows virtual-key codes over a socket
 * into the Proton container; the native build sends the same events straight to the engine.
 * API kept so {@code controls.GamepadInput} works unchanged.
 */
public final class InputBridge {
    public InputBridge() {}

    /** @param vk Windows virtual-key code (VK_*, 1/2/4 = mouse left/right/middle). */
    public void key(int vk, boolean down) {
        int code = KeyMap.vkToEvdev(vk);
        if (code > 0) NativeInput.key(code, down);
    }

    public void mouseMove(float dx, float dy) {
        NativeInput.mouseMove(dx, dy);
    }

    public void wheel(int clicks) {
        NativeInput.scroll(clicks);
    }

    public void close() {}
}
