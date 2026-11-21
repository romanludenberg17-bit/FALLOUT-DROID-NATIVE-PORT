package com.wastelanddroid.controls;

import android.view.Choreographer;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import com.wastelanddroid.runtime.InputBridge;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public final class GamepadInput implements Choreographer.FrameCallback {
    private static final float LOOK_PX_PER_FRAME = 22.0f;
    private static final int VK_A = 65;
    private static final int VK_D = 68;
    private static final int VK_S = 83;
    private static final int VK_W = 87;
    private boolean a;
    private boolean d;
    private final InputBridge input;
    private boolean looping;
    private float lx;
    private float ly;
    private final GamepadMapping map;
    private boolean running;
    private float rx;
    private float ry;
    private boolean s;
    private boolean w;
    private final Set<Integer> held = new HashSet();
    private final Set<Integer> toggledOn = new HashSet();

    public GamepadInput(InputBridge inputBridge, GamepadMapping gamepadMapping) {
        this.input = inputBridge;
        this.map = gamepadMapping;
    }

    public static boolean isGamepad(InputDevice inputDevice) {
        if (inputDevice == null || inputDevice.isVirtual()) {
            return false;
        }
        int sources = inputDevice.getSources();
        return (sources & 1025) == 1025 || (sources & 16777232) == 16777232;
    }

    public static boolean anyConnected() {
        for (int i : InputDevice.getDeviceIds()) {
            if (isGamepad(InputDevice.getDevice(i))) {
                return true;
            }
        }
        return false;
    }

    public void start() {
        this.running = true;
    }

    public void stop() {
        this.running = false;
        if (this.looping) {
            Choreographer.getInstance().removeFrameCallback(this);
        }
        this.looping = false;
        releaseAll();
    }

    private void ensureLooping() {
        if (!this.running || this.looping) {
            return;
        }
        if (this.rx == 0.0f && this.ry == 0.0f) {
            return;
        }
        this.looping = true;
        Choreographer.getInstance().postFrameCallback(this);
    }

    public boolean onKey(KeyEvent keyEvent) {
        if (!isGamepad(keyEvent.getDevice()) && !KeyEvent.isGamepadButton(keyEvent.getKeyCode())) {
            return false;
        }
        int keyCode = keyEvent.getKeyCode();
        if (keyCode == 104) {
            keyCode = GamepadMapping.PAD_LT;
        } else if (keyCode != 105) {
            switch (keyCode) {
                case 19:
                    keyCode = GamepadMapping.PAD_UP;
                    break;
                case 20:
                    keyCode = GamepadMapping.PAD_DOWN;
                    break;
                case 21:
                    keyCode = GamepadMapping.PAD_LEFT;
                    break;
                case 22:
                    keyCode = GamepadMapping.PAD_RIGHT;
                    break;
            }
        } else {
            keyCode = GamepadMapping.PAD_RT;
        }
        if (keyEvent.getRepeatCount() > 0) {
            return true;
        }
        if (keyEvent.getAction() == 0) {
            button(keyCode, true);
        } else if (keyEvent.getAction() == 1) {
            button(keyCode, false);
        }
        return true;
    }

    public boolean onMotion(MotionEvent motionEvent) {
        if (!isGamepad(motionEvent.getDevice()) || motionEvent.getAction() != 2) {
            return false;
        }
        InputDevice device = motionEvent.getDevice();
        this.lx = axis(motionEvent, device, 0);
        this.ly = axis(motionEvent, device, 1);
        this.rx = axis(motionEvent, device, 11);
        this.ry = axis(motionEvent, device, 14);
        float fMax = Math.max(motionEvent.getAxisValue(17), motionEvent.getAxisValue(23));
        float fMax2 = Math.max(motionEvent.getAxisValue(18), motionEvent.getAxisValue(22));
        trigger(GamepadMapping.PAD_LT, fMax);
        trigger(GamepadMapping.PAD_RT, fMax2);
        float axisValue = motionEvent.getAxisValue(15);
        float axisValue2 = motionEvent.getAxisValue(16);
        hat(GamepadMapping.PAD_LEFT, axisValue < -0.5f);
        hat(GamepadMapping.PAD_RIGHT, axisValue > 0.5f);
        hat(GamepadMapping.PAD_UP, axisValue2 < -0.5f);
        hat(GamepadMapping.PAD_DOWN, axisValue2 > 0.5f);
        updateMove();
        ensureLooping();
        return true;
    }

    private float axis(MotionEvent motionEvent, InputDevice inputDevice, int i) {
        float axisValue = motionEvent.getAxisValue(i);
        InputDevice.MotionRange motionRange = inputDevice.getMotionRange(i, motionEvent.getSource());
        float fMax = motionRange != null ? Math.max(motionRange.getFlat(), this.map.deadzone) : this.map.deadzone;
        if (Math.abs(axisValue) < fMax) {
            return 0.0f;
        }
        return (Math.signum(axisValue) * (Math.abs(axisValue) - fMax)) / (1.0f - fMax);
    }

    private void trigger(int i, float f) {
        boolean z = f > 0.45f;
        if (z != this.held.contains(Integer.valueOf(i))) {
            button(i, z);
        }
    }

    private void hat(int i, boolean z) {
        if (z != this.held.contains(Integer.valueOf(i))) {
            button(i, z);
        }
    }

    private void button(int i, boolean z) {
        Integer num = this.map.keys.get(Integer.valueOf(i));
        Set<Integer> set = this.held;
        Integer numValueOf = Integer.valueOf(i);
        if (z) {
            set.add(numValueOf);
        } else {
            set.remove(numValueOf);
        }
        if (num == null || this.input == null) {
            return;
        }
        if (!this.map.toggles.contains(Integer.valueOf(i))) {
            this.input.key(num.intValue(), z);
            return;
        }
        if (z) {
            boolean zContains = this.toggledOn.contains(Integer.valueOf(i));
            boolean z2 = !zContains;
            if (zContains) {
                this.toggledOn.remove(Integer.valueOf(i));
            } else {
                this.toggledOn.add(Integer.valueOf(i));
            }
            this.input.key(num.intValue(), z2);
        }
    }

    private void updateMove() {
        float f = this.ly;
        boolean z = f < -0.35f;
        float f2 = this.lx;
        setMove(z, f2 < -0.35f, f > 0.35f, f2 > 0.35f);
    }

    private void setMove(boolean z, boolean z2, boolean z3, boolean z4) {
        InputBridge inputBridge = this.input;
        if (inputBridge != null) {
            if (z != this.w) {
                inputBridge.key(VK_W, z);
            }
            if (z2 != this.a) {
                this.input.key(65, z2);
            }
            if (z3 != this.s) {
                this.input.key(VK_S, z3);
            }
            if (z4 != this.d) {
                this.input.key(68, z4);
            }
        }
        this.w = z;
        this.a = z2;
        this.s = z3;
        this.d = z4;
    }

    @Override // android.view.Choreographer.FrameCallback
    public void doFrame(long j) {
        if (!this.running || (this.rx == 0.0f && this.ry == 0.0f)) {
            this.looping = false;
            return;
        }
        if (this.input != null) {
            float f = this.map.lookSpeed * LOOK_PX_PER_FRAME;
            float f2 = this.rx;
            float f3 = (f2 * f2 * f2 * 0.6f) + (f2 * 0.4f);
            float f4 = this.ry;
            float f5 = (f4 * f4 * f4 * 0.6f) + (f4 * 0.4f);
            InputBridge inputBridge = this.input;
            float f6 = f3 * f;
            if (this.map.invertY) {
                f5 = -f5;
            }
            inputBridge.mouseMove(f6, f5 * f);
        }
        Choreographer.getInstance().postFrameCallback(this);
    }

    public void releaseAll() {
        InputBridge inputBridge;
        Iterator it = new HashSet(this.held).iterator();
        while (it.hasNext()) {
            button(((Integer) it.next()).intValue(), false);
        }
        Iterator it2 = new HashSet(this.toggledOn).iterator();
        while (it2.hasNext()) {
            Integer num = this.map.keys.get((Integer) it2.next());
            if (num != null && (inputBridge = this.input) != null) {
                inputBridge.key(num.intValue(), false);
            }
        }
        this.toggledOn.clear();
        this.ry = 0.0f;
        this.rx = 0.0f;
        this.ly = 0.0f;
        this.lx = 0.0f;
        setMove(false, false, false, false);
    }
}
