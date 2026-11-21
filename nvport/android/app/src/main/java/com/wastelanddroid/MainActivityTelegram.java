package com.wastelanddroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.os.PowerManager;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import com.wastelanddroid.engine.FalloutEngine;
import com.wastelanddroid.input.ScreenKey;
import com.wastelanddroid.input.ScreenKeyStore;
import com.wastelanddroid.engine.FalloutInput;
import com.wastelanddroid.game.GameDef;
import com.wastelanddroid.game.GameFiles;
import com.wastelanddroid.runtime.ModManager;
import com.wastelanddroid.util.Prefs;
import com.wastelanddroid.util.Ui;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

public class MainActivityTelegram implements FalloutEngine.Progress, Runnable, Consumer {
    public static int installPct;
    public static int installState;
    public static MainActivity sActivity;

    public static void applyLayoutString(Context context, String str) {
        List<ScreenKey> keys = ScreenKeyStore.decode(str);
        if (!keys.isEmpty()) {
            ScreenKeyStore.save(context, keys);
        }
    }

    public static void attachOrSync(MainActivity mainActivity) throws IOException {
        View viewFindViewById = mainActivity.findViewById(2131457911);
        if (viewFindViewById != null) {
            WebView webView = (WebView) viewFindViewById;
            if (mainActivity.launching) {
                mainActivity.launching = false;
                webView.evaluateJavascript("if(window.onNativeActivityPause)window.onNativeActivityPause();", null);
                webView.onPause();
                return;
            } else {
                Ui.applyAccent(mainActivity);
                setupScreenKeys(mainActivity);
                webView.onResume();
                syncToJs(mainActivity, webView);
                return;
            }
        }
        WebView webView2 = new WebView(mainActivity);
        webView2.setId(2131457911);
        webView2.setBackgroundColor(-16777216);
        WebSettings settings = webView2.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        webView2.setWebViewClient(new PipBoyBridge(mainActivity, webView2));
        Ui.applyAccent(mainActivity);
        setupScreenKeys(mainActivity);
        mainActivity.setContentView(webView2);
        webView2.loadUrl("file:///android_asset/ui/index.html");
    }

    public static void extractIcon(MainActivity mainActivity, String str) throws IOException {
        File file = new File(mainActivity.getFilesDir(), "screenkeys");
        file.mkdirs();
        File file2 = new File(file, str);
        InputStream inputStreamOpen = mainActivity.getAssets().open("screenkeys/" + str);
        FileOutputStream fileOutputStream = new FileOutputStream(file2);
        byte[] bArr = new byte[8192];
        while (true) {
            int i = inputStreamOpen.read(bArr);
            if (i <= 0) {
                inputStreamOpen.close();
                fileOutputStream.close();
                return;
            }
            fileOutputStream.write(bArr, 0, i);
        }
    }

    /** Escapes a value for a single-quoted JS string literal. */
    static String jsEsc(String v) {
        if (v == null) return "";
        return v.replace("\\", "\\\\").replace("'", "\\'").replace("\r", "\\r").replace("\n", "\\n");
    }

    public static int getGameDlcs(GameDef gameDef, String str) {
        if (str.length() == 0) {
            return 0;
        }
        return GameFiles.validate(gameDef, new File(str)).dlcs.size();
    }

    public static String getSafeGamePath(MainActivity mainActivity, GameDef gameDef) {
        String strGameDir = gameDef.gameDir(mainActivity);
        if (strGameDir == null) {
            strGameDir = "";
        }
        return strGameDir.replace("\\", "\\\\").replace("'", "\\'");
    }

    public static String iconForCode(int i) {
        return i != 272 ? i != 273 ? i != 18 ? i != 57 ? i != 19 ? i != 47 ? i != 29 ? i != 58 ? i != 15 ? i != 33 ? i != 1 ? i != 63 ? i != 67 ? i != 20 ? i != 28 ? i != 44 ? "" : "ctrl_grab_z.png" : "ctrl_enter.png" : "ctrl_console_tilde.png" : "pip_geck.png" : "pip_platinum_chip.png" : "fc_terminal.png" : "ctrl_camera_f.png" : "ctrl_pipboy_tab.png" : "ctrl_run_capslock.png" : "pip_sneak_ctrl.png" : "ctrl_vats_v.png" : "ctrl_reload_r.png" : "ctrl_jump_space.png" : "ctrl_use_e.png" : "ctrl_scope_rmb.png" : "ctrl_shoot_lmb.png";
    }

    public static int isGameValid(GameDef gameDef, String str) {
        if (str.length() == 0) {
            return 0;
        }
        return GameFiles.validate(gameDef, new File(str)).ok() ? 1 : 0;
    }

    public static int isPermGranted(MainActivity mainActivity, String str) {
        return mainActivity.checkSelfPermission(str) == 0 ? 1 : 0;
    }

    public static int isStorageGranted() {
        return Environment.isExternalStorageManager() ? 1 : 0;
    }

    public static void setupScreenKeys(MainActivity mainActivity) throws IOException {
        ScreenKeyStore.setTint(mainActivity, Ui.FG);
        SharedPreferences sharedPreferences = Prefs.get(mainActivity);
        if (sharedPreferences.getBoolean("fd_layout_v1131", false)) {
            return;
        }
        extractIcon(mainActivity, "ctrl_shoot_lmb.png");
        extractIcon(mainActivity, "ctrl_scope_rmb.png");
        extractIcon(mainActivity, "ctrl_use_e.png");
        extractIcon(mainActivity, "ctrl_jump_space.png");
        extractIcon(mainActivity, "ctrl_reload_r.png");
        extractIcon(mainActivity, "ctrl_vats_v.png");
        extractIcon(mainActivity, "pip_sneak_ctrl.png");
        extractIcon(mainActivity, "ctrl_run_capslock.png");
        extractIcon(mainActivity, "ctrl_pipboy_tab.png");
        extractIcon(mainActivity, "ctrl_camera_f.png");
        extractIcon(mainActivity, "fc_terminal.png");
        extractIcon(mainActivity, "pip_platinum_chip.png");
        extractIcon(mainActivity, "pip_geck.png");
        extractIcon(mainActivity, "ctrl_console_tilde.png");
        extractIcon(mainActivity, "ctrl_enter.png");
        extractIcon(mainActivity, "ctrl_grab_z.png");
        applyLayoutString(mainActivity, "17+0+32+0+31+0+30+0:0.13,0.70,104::WASD::0.88:6:0:1;272:0.90,0.62,76:::ctrl_shoot_lmb.png:0.90:2:0:0;273:0.92,0.34,54:::ctrl_scope_rmb.png:0.85:2:0:0;18:0.75,0.84,54:::ctrl_use_e.png:0.85:2:0:0;28:0.87,0.85,52:::ctrl_enter.png:0.88:2:0:0;57:0.77,0.54,52:::ctrl_jump_space.png:0.85:2:0:0;19:0.64,0.85,48:::ctrl_reload_r.png:0.85:2:0:0;44:0.53,0.85,48:::ctrl_grab_z.png:0.85:2:0:0;47:0.65,0.63,52:::ctrl_vats_v.png:0.88:2:0:0;29:0.06,0.40,48::СКРЫТ.:pip_sneak_ctrl.png:0.82:2:1:1;58:0.06,0.24,44:::ctrl_run_capslock.png:0.78:2:1:0;15:0.94,0.11,52:::ctrl_pipboy_tab.png:0.88:2:0:0;33:0.84,0.11,44:::ctrl_camera_f.png:0.82:2:0:0;1:0.125,0.11,44:::fc_terminal.png:0.85:2:0:0;63:0.20,0.11,42::СОХР.:pip_platinum_chip.png:0.80:2:0:1;67:0.275,0.11,42::ЗАГР.:pip_geck.png:0.80:2:0:1;41:0.745,0.11,42::КОНСОЛЬ:ctrl_console_tilde.png:0.80:2:0:1");
        sharedPreferences.edit().putBoolean("fd_layout_v1131", true).apply();
    }

    public static void setupSplashLogo(LinearLayout linearLayout) throws IOException {
        Context context = linearLayout.getContext();
        InputStream inputStreamOpen = context.getAssets().open("art/logo_fallout_droid.png");
        Bitmap bitmapDecodeStream = BitmapFactory.decodeStream(inputStreamOpen);
        inputStreamOpen.close();
        ImageView imageView = new ImageView(context);
        imageView.setImageBitmap(bitmapDecodeStream);
        imageView.setAdjustViewBounds(true);
        linearLayout.addView(imageView, new LinearLayout.LayoutParams(Ui.dp(context, 420.0f), -2));
    }

    /** Native build: the engine is inside the APK; "install" only unpacks its data files. */
    public static void startInstall(MainActivity mainActivity) {
        sActivity = mainActivity;
        new Thread(new MainActivityTelegram()).start();
    }

    public static void syncToJs(MainActivity mainActivity, WebView webView) {
        StringBuilder sb = new StringBuilder();
        SharedPreferences sharedPreferences = Prefs.get(mainActivity);
        GameDef gameDef = GameDef.FNV;
        String safeGamePath = getSafeGamePath(mainActivity, gameDef);
        sb.append("if(window.__fdSyncNative)window.__fdSyncNative({fnvPath:'");
        sb.append(safeGamePath);
        int iIsGameValid = isGameValid(gameDef, safeGamePath);
        sb.append("',fnvValid:");
        sb.append(iIsGameValid);
        int gameDlcs = getGameDlcs(gameDef, safeGamePath);
        sb.append(",fnvDlc:");
        sb.append(gameDlcs);
        GameDef gameDef2 = GameDef.FO3;
        String safeGamePath2 = getSafeGamePath(mainActivity, gameDef2);
        sb.append(",fo3Path:'");
        sb.append(safeGamePath2);
        int iIsGameValid2 = isGameValid(gameDef2, safeGamePath2);
        sb.append("',fo3Valid:");
        sb.append(iIsGameValid2);
        int gameDlcs2 = getGameDlcs(gameDef2, safeGamePath2);
        sb.append(",fo3Dlc:");
        sb.append(gameDlcs2);
        boolean yVar = FalloutEngine.ready(mainActivity);
        sb.append(",engineReady:");
        sb.append(yVar ? 1 : 0);
        int iIsStorageGranted = isStorageGranted();
        sb.append(",permStorage:");
        sb.append(iIsStorageGranted);
        int iIsPermGranted = isPermGranted(mainActivity, "android.permission.RECORD_AUDIO");
        sb.append(",permAudio:");
        sb.append(iIsPermGranted);
        boolean zIsIgnoringBatteryOptimizations = ((PowerManager) mainActivity.getSystemService("power")).isIgnoringBatteryOptimizations(mainActivity.getPackageName());
        sb.append(",permNotif:");
        sb.append(zIsIgnoringBatteryOptimizations ? 1 : 0);
        int i = installState;
        sb.append(",installState:");
        sb.append(i);
        int i2 = installPct;
        sb.append(",installPct:");
        sb.append(i2);
        String strAccent = Ui.accent(mainActivity);
        sb.append(",theme:'");
        sb.append(strAccent);
        String string = sharedPreferences.getString(Prefs.QUALITY, "medium");
        sb.append("',preset:'");
        sb.append(string);
        String string2 = sharedPreferences.getString("resolution", "1280x720");
        sb.append("',res:'");
        sb.append(string2);
        boolean z = sharedPreferences.getBoolean(Prefs.USE_FOSE, true);
        sb.append("',fose:");
        sb.append(z ? 1 : 0);
        int i3 = sharedPreferences.getInt(FalloutInput.LOOK_SENS, 115);
        sb.append(",mouseSpeed:");
        sb.append(i3);
        String strListMods = ModManager.listMods(gameDef.gameDir(mainActivity));
        sb.append(",fnvAllMods:'");
        sb.append(jsEsc(strListMods));
        String plugins = ModManager.readPlugins(gameDef.gameDir(mainActivity));
        sb.append("',fnvActiveMods:'");
        sb.append(jsEsc(plugins));
        String strListMods2 = ModManager.listMods(gameDef2.gameDir(mainActivity));
        sb.append("',fo3AllMods:'");
        sb.append(jsEsc(strListMods2));
        String plugins2 = ModManager.readPlugins(gameDef2.gameDir(mainActivity));
        sb.append("',fo3ActiveMods:'");
        sb.append(jsEsc(plugins2));
        sb.append("'});");
        webView.evaluateJavascript(sb.toString(), null);
    }

    @Override // java.util.function.Consumer
    public void accept(Object obj) {
        try {
        if (obj instanceof MainActivity) {
            attachOrSync((MainActivity) obj);
        } else {
            setupSplashLogo((LinearLayout) obj);
        }
            } catch (RuntimeException __re) {
            throw __re;
        } catch (Exception __e) {
            throw new RuntimeException(__e);
        }
    }

    @Override // com.wastelanddroid.engine.FalloutEngine.Progress
    public void on(String str, int i) {
        if (installState != 2) {
            installPct = (i >> 1) + 5;
            return;
        }
        int i2 = (i >> 1) + 55;
        if (99 < i2) {
            i2 = 99;
        }
        installPct = i2;
    }

    @Override // java.lang.Runnable
    public void run() {
        MainActivity mainActivity = sActivity;
        if (mainActivity == null) return;
        installState = 1;
        installPct = 10;
        try {
            FalloutEngine.ensureEngineData(mainActivity);
        } catch (IOException e) {
            installState = 0;
            installPct = 0;
            return;
        }
        Prefs.get(mainActivity).edit().putBoolean(MainActivity.ONBOARDED, true).apply();
        installPct = 100;
        installState = 3;
    }
}
