package com.wastelanddroid.engine;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * Loading a save (or back to the main menu) inside a game: the engine starts again in a
 * fresh process. This activity, in the launcher's process and showing only the game's
 * loading picture (its theme), waits for the old game process to end (its memory free) and
 * starts the game again — the player sees one loading screen, never the launcher.
 */
public final class GameRelayActivity extends Activity {
    public static final String EXTRA_GAME = "game";
    private final Handler h = new Handler(Looper.getMainLooper());
    private long since;
    private String game;

    @Override
    protected void onCreate(Bundle state) {
        game = getIntent().getStringExtra(EXTRA_GAME);
        if ("fo3".equals(game)) setTheme(com.wastelanddroid.R.style.FdGameThemeFo3);
        super.onCreate(state);
        since = SystemClock.uptimeMillis();
        h.post(wait);
    }

    private final Runnable wait = new Runnable() {
        @Override
        public void run() {
            if (FalloutEngine.gameProcessAlive(GameRelayActivity.this) && SystemClock.uptimeMillis() - since < 8000) {
                h.postDelayed(this, 80);
                return;
            }
            Intent g = new Intent(GameRelayActivity.this, NativeGameActivityFo3.forGame(game)).putExtra("game", game)
                    .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(g);
            overridePendingTransition(0, 0);
            finish();
            overridePendingTransition(0, 0);
        }
    };

    @Override
    public void onBackPressed() {
        // Nothing to go back to while the game starts again.
    }
}
