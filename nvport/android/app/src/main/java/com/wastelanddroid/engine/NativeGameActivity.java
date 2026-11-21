package com.wastelanddroid.engine;

import android.os.Bundle;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import com.google.androidgamesdk.GameActivity;
import com.wastelanddroid.controls.GamepadInput;
import com.wastelanddroid.controls.GamepadMapping;
import com.wastelanddroid.runtime.InputBridge;
import com.wastelanddroid.input.KeyMap;
import com.wastelanddroid.input.ScreenKeysOverlay;

/**
 * The game: GameActivity running libnvforever.so (android_main), with Fallout Droid's
 * on-screen controls over it. The command line was written by {@link FalloutEngine#launch}.
 */
public class NativeGameActivity extends GameActivity {
    private ScreenKeysOverlay overlay;
    private GamepadInput gamepad;

    @Override
    protected void onCreate(Bundle state) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onCreate(state);
        hideSystemBars();
        overlay = new ScreenKeysOverlay(this);
        addContentView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        GamepadMapping mapping;
        try {
            mapping = GamepadMapping.load(this);
        } catch (Exception e) {
            mapping = GamepadMapping.defaults();
        }
        gamepad = new GamepadInput(new InputBridge(), mapping);
        gamepad.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        if (overlay != null) overlay.reload();
    }

    @Override
    protected void onPause() {
        if (overlay != null) overlay.releaseAll();
        if (gamepad != null) gamepad.releaseAll();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (gamepad != null) gamepad.stop();
        super.onDestroy();
    }

    private void hideSystemBars() {
        WindowInsetsController c = getWindow().getInsetsController();
        if (c != null) {
            c.hide(WindowInsets.Type.systemBars());
            c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    // ---- physical keyboard and gamepad -> engine (evdev codes)
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int src = e.getSource();
        if (gamepad != null && ((src & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (src & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK)) {
            if (gamepad.onKey(e)) return true;
        }
        int code = KeyMap.toEvdev(e.getKeyCode());
        if (code > 0 && (e.getAction() == KeyEvent.ACTION_DOWN || e.getAction() == KeyEvent.ACTION_UP)) {
            if (e.getRepeatCount() == 0) NativeInput.key(code, e.getAction() == KeyEvent.ACTION_DOWN);
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    // ---- physical mouse: captured -> relative look; otherwise absolute cursor
    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent e) {
        if (gamepad != null && (e.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
            if (gamepad.onMotion(e)) return true;
        }
        if (e.isFromSource(InputDevice.SOURCE_MOUSE)) {
            handleMouse(e, false);
            return true;
        }
        return super.dispatchGenericMotionEvent(e);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        if (e.isFromSource(InputDevice.SOURCE_MOUSE)) {
            handleMouse(e, false);
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN && overlay != null) overlay.requestPointerCapture();
            return true;
        }
        return super.dispatchTouchEvent(e);
    }

    private int lastButtons;

    private void handleMouse(MotionEvent e, boolean captured) {
        if (captured) {
            NativeInput.mouseMove(e.getX(), e.getY());
        } else {
            NativeInput.cursorTo(e.getX(), e.getY());
        }
        if (e.getActionMasked() == MotionEvent.ACTION_SCROLL) {
            NativeInput.scroll(e.getAxisValue(MotionEvent.AXIS_VSCROLL));
        }
        int b = e.getButtonState();
        int changed = b ^ lastButtons;
        if ((changed & MotionEvent.BUTTON_PRIMARY) != 0) NativeInput.key(272, (b & MotionEvent.BUTTON_PRIMARY) != 0);
        if ((changed & MotionEvent.BUTTON_SECONDARY) != 0) NativeInput.key(273, (b & MotionEvent.BUTTON_SECONDARY) != 0);
        if ((changed & MotionEvent.BUTTON_TERTIARY) != 0) NativeInput.key(274, (b & MotionEvent.BUTTON_TERTIARY) != 0);
        lastButtons = b;
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {
        super.onPointerCaptureChanged(hasCapture);
        if (hasCapture && overlay != null) {
            overlay.setOnCapturedPointerListener((View v, MotionEvent e) -> {
                handleMouse(e, true);
                return true;
            });
        }
    }
}
