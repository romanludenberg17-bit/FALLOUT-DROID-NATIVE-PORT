package com.mafia2re.game;
/** Compile-time stub: the real class (with its JNI natives bound by name in libmain.so) is in classes.dex. */
public class TouchControls extends android.view.View {
    public TouchControls(android.content.Context c) { super(c); }
    static native float nativeFps();
    static native float nativeFrameMs();
    static native int nativeGameState();
    static native int nativeMemoryMiB();
    static native void nativeSetFpsLimit(int fps);
}
