package com.wastelanddroid;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import com.wastelanddroid.engine.FalloutEngine;
import com.wastelanddroid.game.GameDef;
import com.wastelanddroid.game.GameFiles;
import com.wastelanddroid.util.Prefs;
import com.wastelanddroid.util.Ui;
import com.wastelanddroid.util.WastelandBackdrop;
import java.io.File;
import java.util.function.Consumer;

public final class SplashActivity extends Activity {
    private static final long DURATION_MS = 3200;
    private boolean done;
    private final Handler handler = new Handler(Looper.getMainLooper());

    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        // The launcher icon over a running app (the launcher or the game in it): show what
        // was open, as it was. Starting the launcher again would close the game above it.
        if (!isTaskRoot() && getIntent() != null && android.content.Intent.ACTION_MAIN.equals(getIntent().getAction())
                && getIntent().hasCategory(android.content.Intent.CATEGORY_LAUNCHER)) {
            finish();
            return;
        }
        Ui.immersive(this);
        FrameLayout frameLayout = new FrameLayout(this);
        frameLayout.setBackgroundColor(-16777216);
        WastelandBackdrop wastelandBackdrop = new WastelandBackdrop(this);
        frameLayout.addView(wastelandBackdrop, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout linearLayoutColumn = Ui.column(this);
        linearLayoutColumn.setGravity(17);
        new MainActivityTelegram().accept(linearLayoutColumn);
        frameLayout.addView(linearLayoutColumn, new FrameLayout.LayoutParams(-2, -2, 17));
        setContentView(frameLayout);
        ObjectAnimator objectAnimatorOfFloat = ObjectAnimator.ofFloat(wastelandBackdrop, "zoom", 1.0f, 1.12f);
        objectAnimatorOfFloat.setDuration(4700L);
        objectAnimatorOfFloat.setInterpolator(new LinearInterpolator());
        objectAnimatorOfFloat.start();
        wastelandBackdrop.setAlpha(0.0f);
        wastelandBackdrop.animate().alpha(1.0f).setDuration(900L).start();
        frameLayout.setOnClickListener(new View.OnClickListener() { // from class: com.wastelanddroid.SplashActivity$$ExternalSyntheticLambda0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SplashActivity.this.lambda$onCreate$0(view);
            }
        });
        this.handler.postDelayed(new Runnable() { // from class: com.wastelanddroid.SplashActivity$$ExternalSyntheticLambda1
            @Override // java.lang.Runnable
            public final void run() {
                SplashActivity.this.next();
            }
        }, DURATION_MS);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$onCreate$0(View view) {
        next();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void next() {
        if (this.done) {
            return;
        }
        this.done = true;
        Intent intent = new Intent(this, (Class<?>) MainActivity.class);
        GameDef gameDefAutostartGame = autostartGame();
        if (gameDefAutostartGame != null) {
            intent.putExtra("autostart", gameDefAutostartGame.id);
        }
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private GameDef autostartGame() {
        String string;
        GameDef gameDefById;
        String strGameDir;
        if (Prefs.get(this).getBoolean(Prefs.LAUNCH_PENDING, false)) {
            Prefs.get(this).edit().putBoolean(Prefs.LAUNCH_PENDING, false).putBoolean(Prefs.AUTOSTART_BLOCKED, true).putBoolean(Prefs.CRASH_NOTICE, true).apply();
            return null;
        }
        if (Prefs.get(this).getBoolean("autostart", false) && !Prefs.get(this).getBoolean(Prefs.AUTOSTART_BLOCKED, false) && (string = Prefs.get(this).getString(Prefs.LAST_GAME, null)) != null && (strGameDir = (gameDefById = GameDef.byId(string)).gameDir(this)) != null && GameFiles.validate(gameDefById, new File(strGameDir)).ok() && FalloutEngine.ready(this) && Prefs.get(this).getBoolean(MainActivity.ONBOARDED, false)) {
            return gameDefById;
        }
        return null;
    }

    @Override // android.app.Activity
    protected void onDestroy() {
        this.handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override // android.app.Activity, android.view.Window.Callback
    public void onWindowFocusChanged(boolean z) {
        super.onWindowFocusChanged(z);
        if (z) {
            Ui.immersive(this);
        }
    }
}
