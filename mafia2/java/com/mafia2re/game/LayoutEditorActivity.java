package com.mafia2re.game;

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;

/** Full-screen layout editor opened from the launcher, over a dimmed city backdrop. */
public class LayoutEditorActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setBackgroundDrawable(new ColorDrawable(Ui.BG));
        if (Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        hideBars();
        FrameLayout root = new FrameLayout(this);
        CityBackdrop bg = new CityBackdrop(this);
        bg.setAnimated(false);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        View dim = new View(this);
        dim.setBackgroundColor(0x99000000);
        root.addView(dim, new FrameLayout.LayoutParams(-1, -1));
        ControlsOverlay overlay = new ControlsOverlay(this, new GameConfig(this), false);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        root.addView(new EditorChrome(this, overlay, ControlLayout.FOOT, this::finish), new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    @Override
    public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        if (f) hideBars();
    }

    private void hideBars() {
        getWindow().getDecorView(); // installs the decor: getInsetsController() is null-unsafe before that
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }
}
