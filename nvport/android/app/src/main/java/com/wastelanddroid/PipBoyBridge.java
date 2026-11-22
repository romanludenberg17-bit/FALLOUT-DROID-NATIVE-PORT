package com.wastelanddroid;

import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.wastelanddroid.controls.ControlsEditorActivity;
import com.wastelanddroid.controls.GamepadEditor;
import com.wastelanddroid.engine.FalloutInput;
import com.wastelanddroid.game.GameDef;
import com.wastelanddroid.game.GraphicsActivity;
import com.wastelanddroid.game.GraphicsSettings;
import com.wastelanddroid.mods.ModManagerActivity;
import com.wastelanddroid.runtime.CoreModInstaller;
import com.wastelanddroid.runtime.ModManager;
import com.wastelanddroid.runtime.NexusInAppBrowser;
import com.wastelanddroid.saves.SavesActivity;
import com.wastelanddroid.util.Links;
import com.wastelanddroid.util.Prefs;
import com.wastelanddroid.util.Ui;
import java.io.IOException;
import org.json.JSONException;

public class PipBoyBridge extends WebViewClient {
    private final MainActivity activity;
    private final WebView webView;

    public PipBoyBridge(MainActivity mainActivity, WebView webView) {
        this.activity = mainActivity;
        this.webView = webView;
    }

    private void openGameScreen(Class cls, String str) {
        MainActivity mainActivity = this.activity;
        Intent intent = new Intent(mainActivity, (Class<?>) cls);
        intent.putExtra("game", str);
        mainActivity.startActivity(intent);
    }

    public boolean handleUrl(String str) throws JSONException, IOException {
        MainActivity mainActivity = this.activity;
        WebView webView = this.webView;
        if (str == null) {
            return false;
        }
        if (str.startsWith("http")) {
            mainActivity.startActivity(new Intent("android.intent.action.VIEW", Uri.parse(str)));
            return true;
        }
        if (!str.startsWith("falloutpicker://")) {
            return false;
        }
        String strSubstring = str.substring(16);
        if (strSubstring.equals("play_fnv")) {
            mainActivity.onPlay(GameDef.FNV);
            return true;
        }
        if (strSubstring.equals("play_fo3")) {
            mainActivity.onPlay(GameDef.FO3);
            return true;
        }
        if (strSubstring.equals("folder_fnv")) {
            mainActivity.pendingGame = GameDef.FNV;
            mainActivity.pickGameDir();
            return true;
        }
        if (strSubstring.equals("folder_fo3")) {
            mainActivity.pendingGame = GameDef.FO3;
            mainActivity.pickGameDir();
            return true;
        }
        if (strSubstring.equals("verify_fnv")) {
            MainActivityTelegram.syncToJs(mainActivity, webView);
            return true;
        }
        if (strSubstring.equals("verify_fo3")) {
            MainActivityTelegram.syncToJs(mainActivity, webView);
            return true;
        }
        if (strSubstring.equals("mods_fnv")) {
            openGameScreen(ModManagerActivity.class, "fnv");
            return true;
        }
        if (strSubstring.equals("mods_fo3")) {
            openGameScreen(ModManagerActivity.class, "fo3");
            return true;
        }
        if (strSubstring.equals("saves_fnv")) {
            openGameScreen(SavesActivity.class, "fnv");
            return true;
        }
        if (strSubstring.equals("saves_fo3")) {
            openGameScreen(SavesActivity.class, "fo3");
            return true;
        }
        if (strSubstring.equals("perm_storage")) {
            if (!Environment.isExternalStorageManager()) {
                mainActivity.startActivity(new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION", Uri.parse("package:" + mainActivity.getPackageName())));
            }
            MainActivityTelegram.syncToJs(mainActivity, webView);
            return true;
        }
        if (strSubstring.equals("perm_audio")) {
            if (MainActivityTelegram.isPermGranted(mainActivity, "android.permission.RECORD_AUDIO") == 0) {
                mainActivity.requestPermissions(new String[]{"android.permission.RECORD_AUDIO"}, 1004);
            }
            MainActivityTelegram.syncToJs(mainActivity, webView);
            return true;
        }
        if (strSubstring.equals("perm_notif")) {
            mainActivity.startActivity(new Intent("android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", Uri.parse("package:" + mainActivity.getPackageName())));
            MainActivityTelegram.syncToJs(mainActivity, webView);
            return true;
        }
        if (strSubstring.equals("install_engine")) {
            MainActivityTelegram.startInstall(mainActivity);
            MainActivityTelegram.syncToJs(mainActivity, webView);
            return true;
        }
        if (strSubstring.equals("telegram")) {
            mainActivity.startActivity(new Intent("android.intent.action.VIEW", Uri.parse(Links.TELEGRAM_URL)));
            return true;
        }
        if (strSubstring.equals("onboarding")) {
            MainActivityTelegram.syncToJs(mainActivity, webView);
            return true;
        }
        if (strSubstring.equals("perm_app_settings")) {
            mainActivity.startActivity(new Intent("android.settings.APPLICATION_DETAILS_SETTINGS", Uri.parse("package:" + mainActivity.getPackageName())));
            return true;
        }
        if (strSubstring.startsWith("savelayout_")) {
            MainActivityTelegram.applyLayoutString(mainActivity, Uri.decode(strSubstring.substring(11)));
            return true;
        }
        if (strSubstring.startsWith("savemods_fnv_")) {
            ModManager.savePlugins(GameDef.FNV.gameDir(mainActivity), Uri.decode(strSubstring.substring(13)));
            MainActivityTelegram.syncToJs(mainActivity, webView);
            return true;
        }
        if (strSubstring.startsWith("savemods_fo3_")) {
            ModManager.savePlugins(GameDef.FO3.gameDir(mainActivity), Uri.decode(strSubstring.substring(13)));
            MainActivityTelegram.syncToJs(mainActivity, webView);
            return true;
        }
        if (strSubstring.equals("gfx_auto")) {
            Prefs.get(mainActivity).edit().putBoolean("gfx_auto", true).apply();
            webView.evaluateJavascript("if(window.showToast)showToast('Графика будет настроена под этот телефон при следующем запуске игры');if(window.__fdFxState)window.__fdFxState({terrain:false,shafts:false,ao:false,clouds:false,haze:false,water:false});", null);
            return true;
        }
        if (strSubstring.startsWith("fx_") && !strSubstring.equals("fx_state")) {
            // fx_<name>_on / fx_<name>_off: the launcher's effect switches (FalloutEngine.Profile).
            String[] parts = strSubstring.split("_");
            if (parts.length == 3) {
                Prefs.get(mainActivity).edit().putBoolean("fx_" + parts[1], parts[2].equals("on")).remove("gpu_safe_level").apply();
            }
            return true;
        }
        if (strSubstring.equals("fx_state")) {
            StringBuilder js = new StringBuilder("{");
            for (String k : new String[]{"terrain", "shafts", "ao", "clouds", "haze", "water"}) {
                js.append('"').append(k).append("\":").append(Prefs.get(mainActivity).getBoolean("fx_" + k, false)).append(',');
            }
            js.setLength(js.length() - 1);
            js.append('}');
            webView.evaluateJavascript("if(window.__fdFxState)window.__fdFxState(" + js + ");", null);
            return true;
        }
        if (strSubstring.equals("mod_report")) {
            webView.evaluateJavascript("if(window.__fdModReport)window.__fdModReport(" + com.wastelanddroid.engine.FalloutEngine.modReportJson(mainActivity) + ");", null);
            return true;
        }
        if (strSubstring.equals("check_core_mods")) {
            webView.evaluateJavascript("if(window.__fdModStoreStatus)window.__fdModStoreStatus(" + CoreModInstaller.check(GameDef.FNV.gameDir(mainActivity)) + ");", null);
            return true;
        }
        if (strSubstring.startsWith("install_core_")) {
            webView.evaluateJavascript("if(window.__fdModStoreStatus)window.__fdModStoreStatus(" + CoreModInstaller.install(mainActivity, GameDef.FNV.gameDir(mainActivity), strSubstring.substring(13)) + ",true);", null);
            MainActivityTelegram.syncToJs(mainActivity, webView);
            return true;
        }
        if (strSubstring.startsWith("install_url_")) {
            String rest = strSubstring.substring(12);
            GameDef g = storeGame(rest);
            NexusInAppBrowser.startDirectDownload(mainActivity, webView, g.gameDir(mainActivity), Uri.decode(stripGame(rest)));
            return true;
        }
        if (strSubstring.startsWith("nexus_open_")) {
            String rest = strSubstring.substring(11);
            Prefs.get(mainActivity).edit().putString("store_game", storeGame(rest).id).apply();
            NexusInAppBrowser.open(mainActivity, webView, storeGame(rest).gameDir(mainActivity), Uri.decode(stripGame(rest)));
            return true;
        }
        if (strSubstring.startsWith("list_dir_")) {
            webView.evaluateJavascript("if(window.__fdDirList)window.__fdDirList(" + CoreModInstaller.listDirJson(Uri.decode(strSubstring.substring(9))) + ");", null);
            return true;
        }
        if (strSubstring.startsWith("import_zip_")) {
            String rest = strSubstring.substring(11);
            NexusInAppBrowser.startLocalZipInstall(mainActivity, webView, storeGame(rest).gameDir(mainActivity), Uri.decode(stripGame(rest)));
            return true;
        }
        if (strSubstring.equals("list_plugins_fnv")) {
            webView.evaluateJavascript("if(window.__fdPluginsData)window.__fdPluginsData(" + CoreModInstaller.listPluginsJson(GameDef.FNV.gameDir(mainActivity)) + ");", null);
            return true;
        }
        if (strSubstring.equals("list_plugins_fo3")) {
            webView.evaluateJavascript("if(window.__fdPluginsData)window.__fdPluginsData(" + CoreModInstaller.listPluginsJson(GameDef.FO3.gameDir(mainActivity)) + ");", null);
            return true;
        }
        if (strSubstring.startsWith("touch_cfg_")) {
            CoreModInstaller.saveTouchCfg(mainActivity, strSubstring.substring(10));
            return true;
        }
        if (strSubstring.equals("container")) {
            // Proton container settings of 1.x -> the launcher's settings in the native build.
            mainActivity.startActivity(new Intent(mainActivity, (Class<?>) SettingsActivity.class));
            return true;
        }
        if (strSubstring.equals("native_graphics")) {
            mainActivity.startActivity(new Intent(mainActivity, (Class<?>) GraphicsActivity.class));
            return true;
        }
        if (strSubstring.equals("native_controls")) {
            mainActivity.startActivity(new Intent(mainActivity, (Class<?>) ControlsEditorActivity.class));
            return true;
        }
        if (strSubstring.equals("native_settings")) {
            mainActivity.startActivity(new Intent(mainActivity, (Class<?>) SettingsActivity.class));
            return true;
        }
        if (strSubstring.equals("gamepad_editor")) {
            GamepadEditor.show(mainActivity);
            return true;
        }
        if (strSubstring.equals("share_logs")) {
            SettingsActivity.shareLastLogs(mainActivity);
            return true;
        }
        if (strSubstring.equals("toggle_fose")) {
            Prefs.get(mainActivity).edit().putBoolean(Prefs.USE_FOSE, !Prefs.get(mainActivity).getBoolean(Prefs.USE_FOSE, true)).commit();
            MainActivityTelegram.syncToJs(mainActivity, webView);
            return true;
        }
        if (strSubstring.equals("fose_on")) {
            Prefs.get(mainActivity).edit().putBoolean(Prefs.USE_FOSE, true).commit();
            MainActivityTelegram.syncToJs(mainActivity, webView);
            return true;
        }
        if (strSubstring.equals("fose_off")) {
            Prefs.get(mainActivity).edit().putBoolean(Prefs.USE_FOSE, false).commit();
            MainActivityTelegram.syncToJs(mainActivity, webView);
            return true;
        }
        if (strSubstring.equals("quality_low")) {
            Prefs.get(mainActivity).edit().putString(Prefs.QUALITY, "low").commit();
            GraphicsSettings.resetToPreset(mainActivity);
            MainActivityTelegram.syncToJs(mainActivity, webView);
            return true;
        }
        if (strSubstring.equals("quality_medium")) {
            Prefs.get(mainActivity).edit().putString(Prefs.QUALITY, "medium").commit();
            GraphicsSettings.resetToPreset(mainActivity);
            MainActivityTelegram.syncToJs(mainActivity, webView);
            return true;
        }
        if (strSubstring.equals("quality_high")) {
            Prefs.get(mainActivity).edit().putString(Prefs.QUALITY, "high").commit();
            GraphicsSettings.resetToPreset(mainActivity);
            MainActivityTelegram.syncToJs(mainActivity, webView);
            return true;
        }
        if (strSubstring.equals("quality_ultra")) {
            Prefs.get(mainActivity).edit().putString(Prefs.QUALITY, "high").commit();
            GraphicsSettings.resetToPreset(mainActivity);
            MainActivityTelegram.syncToJs(mainActivity, webView);
            return true;
        }
        if (strSubstring.equals("res_720")) {
            Prefs.get(mainActivity).edit().putString("resolution", "1280x720").putInt(Prefs.RES_SCALE, 75).commit();
            MainActivityTelegram.syncToJs(mainActivity, webView);
            return true;
        }
        if (strSubstring.equals("res_900")) {
            Prefs.get(mainActivity).edit().putString("resolution", "1600x900").putInt(Prefs.RES_SCALE, 85).commit();
            MainActivityTelegram.syncToJs(mainActivity, webView);
            return true;
        }
        if (strSubstring.equals("res_1080")) {
            Prefs.get(mainActivity).edit().putString("resolution", "1920x1080").putInt(Prefs.RES_SCALE, 100).commit();
            MainActivityTelegram.syncToJs(mainActivity, webView);
            return true;
        }
        if (strSubstring.equals("res_native")) {
            Prefs.get(mainActivity).edit().putString("resolution", "1920x1080").putInt(Prefs.RES_SCALE, 100).commit();
            MainActivityTelegram.syncToJs(mainActivity, webView);
            return true;
        }
        if (strSubstring.equals("res_1280x720")) {
            Prefs.get(mainActivity).edit().putString("resolution", "1280x720").putInt(Prefs.RES_SCALE, 75).commit();
            MainActivityTelegram.syncToJs(mainActivity, webView);
            return true;
        }
        if (strSubstring.equals("res_1600x900")) {
            Prefs.get(mainActivity).edit().putString("resolution", "1600x900").putInt(Prefs.RES_SCALE, 85).commit();
            MainActivityTelegram.syncToJs(mainActivity, webView);
            return true;
        }
        if (strSubstring.equals("res_1920x1080")) {
            Prefs.get(mainActivity).edit().putString("resolution", "1920x1080").putInt(Prefs.RES_SCALE, 100).commit();
            MainActivityTelegram.syncToJs(mainActivity, webView);
            return true;
        }
        if (strSubstring.equals("theme_green")) {
            Prefs.get(mainActivity).edit().putString(Ui.ACCENT_KEY, "green").commit();
            Ui.applyAccent(mainActivity);
            MainActivityTelegram.setupScreenKeys(mainActivity);
            MainActivityTelegram.syncToJs(mainActivity, webView);
            return true;
        }
        if (strSubstring.equals("theme_amber")) {
            Prefs.get(mainActivity).edit().putString(Ui.ACCENT_KEY, "orange").commit();
            Ui.applyAccent(mainActivity);
            MainActivityTelegram.setupScreenKeys(mainActivity);
            MainActivityTelegram.syncToJs(mainActivity, webView);
            return true;
        }
        if (strSubstring.equals("theme_blue")) {
            Prefs.get(mainActivity).edit().putString(Ui.ACCENT_KEY, "blue").commit();
            Ui.applyAccent(mainActivity);
            MainActivityTelegram.setupScreenKeys(mainActivity);
            MainActivityTelegram.syncToJs(mainActivity, webView);
            return true;
        }
        if (strSubstring.equals("theme_white")) {
            Prefs.get(mainActivity).edit().putString(Ui.ACCENT_KEY, "white").commit();
            Ui.applyAccent(mainActivity);
            MainActivityTelegram.setupScreenKeys(mainActivity);
            MainActivityTelegram.syncToJs(mainActivity, webView);
            return true;
        }
        if (strSubstring.equals("mouse_direct")) {
            Prefs.get(mainActivity).edit().putString("touch_mode", "direct").commit();
            return true;
        }
        if (strSubstring.equals("mouse_trackpad")) {
            Prefs.get(mainActivity).edit().putString("touch_mode", "trackpad").commit();
            return true;
        }
        if (strSubstring.equals("smart_hide_on") || strSubstring.equals("smart_hide_off")) {
            Prefs.get(mainActivity).edit().putBoolean("smart_hide", strSubstring.endsWith("_on")).commit();
            return true;
        }
        if (strSubstring.equals("mouse_auto")) {
            Prefs.get(mainActivity).edit().putString("touch_mode", "auto").commit();
            return true;
        }
        if (strSubstring.startsWith("setpath_fnv_")) {
            Prefs.get(mainActivity).edit().putString(GameDef.FNV.dirKey(), Uri.decode(strSubstring.substring(12))).commit();
            MainActivityTelegram.syncToJs(mainActivity, webView);
            return true;
        }
        if (strSubstring.startsWith("setpath_fo3_")) {
            Prefs.get(mainActivity).edit().putString(GameDef.FO3.dirKey(), Uri.decode(strSubstring.substring(12))).commit();
            MainActivityTelegram.syncToJs(mainActivity, webView);
            return true;
        }
        if (!strSubstring.startsWith("mouse_")) {
            return true;
        }
        Prefs.get(mainActivity).edit().putInt(FalloutInput.LOOK_SENS, Integer.parseInt(strSubstring.substring(6))).commit();
        MainActivityTelegram.syncToJs(mainActivity, webView);
        return true;
    }

    @Override // android.webkit.WebViewClient
    public void onPageFinished(WebView webView, String str) {
        MainActivityTelegram.syncToJs(this.activity, webView);
    }

    @Override // android.webkit.WebViewClient
    public boolean shouldOverrideUrlLoading(WebView webView, WebResourceRequest webResourceRequest) {
        return safeHandle(webResourceRequest.getUrl().toString());
    }

    @Override // android.webkit.WebViewClient
    public boolean shouldOverrideUrlLoading(WebView webView, String str) {
        return safeHandle(str);
    }

    /** A failing bridge command must not crash the launcher: logged and shown as a toast. */
    private boolean safeHandle(String url) {
        try {
            return handleUrl(url);
        } catch (Exception e) {
            com.wastelanddroid.util.CrashLog.write(this.activity, "bridge", e);
            android.widget.Toast.makeText(this.activity, "Ошибка: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
            return true;
        }
    }

    /** The mod store's game: a "fo3_" or "fnv_" prefix on the scheme's argument (FNV without). */
    private static GameDef storeGame(String rest) {
        return rest.startsWith("fo3_") ? GameDef.FO3 : GameDef.FNV;
    }

    private static String stripGame(String rest) {
        return rest.startsWith("fo3_") || rest.startsWith("fnv_") ? rest.substring(4) : rest;
    }
}
