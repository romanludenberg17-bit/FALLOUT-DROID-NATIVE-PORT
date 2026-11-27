package com.mafia2re.game;

/** Test double: game state and stats are set by the test. */
public class TouchControls extends android.view.View {
    public static int state = 8 | 1;   // known + player in control (on foot)
    public static int fpsLimit = -1;

    public TouchControls(android.content.Context c) { super(c); }

    static float nativeFps() { return 58f; }
    static float nativeFrameMs() { return 17.2f; }
    static int nativeGameState() { return state; }
    static int nativeMemoryMiB() { return 1850; }
    static void nativeSetFpsLimit(int fps) { fpsLimit = fps; }
}
