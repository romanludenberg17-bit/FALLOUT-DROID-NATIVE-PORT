package com.rommy;

import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.PowerManager;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;

/**
 * 4NMP main menu: an HTML home screen (assets/home/index.html) over the 4NMP
 * launcher's own home bar. Every action is the launcher's own button (same
 * ids as its home bar: 1001 play ... 1005 Telegram), so the launcher keeps its
 * install / settings / multiplayer logic. If anything here fails, the original
 * home bar stays usable.
 */
public class NmpHomeActivity extends NmpLauncherActivity {
  private static final String TAG = "NmpHome";
  private static final String PREFS = "nmp_home";
  private WebView home;
  private boolean failed;

  // ------------------------------------------------------------ bundled drivers
  /** Vulkan drivers shipped in assets/drivers/<name>/ and offered in the launcher's driver list. */
  private static final String[] BUNDLED_DRIVERS = {"dd-turnip-emu"};
  private static final String[] DRIVER_FILES = {"libvulkan_freedreno.so", "meta.json", "NOTICE"};

  @Override
  public void onCreate(android.os.Bundle state) {
    installBundledDrivers();  // before the launcher builds its driver list
    nativeRendererOnMali();
    super.onCreate(state);
  }

  /**
   * Mali GPUs (MediaTek, Google Tensor, Unisoc...): the launcher's "Native"
   * renderer (gta4-mobile) once, also for users who kept the old default.
   */
  private void nativeRendererOnMali() {
    try {
      SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
      if (p.getBoolean("mali_native_v637", false)) return;
      p.edit().putBoolean("mali_native_v637", true).apply();
      String vendor = "", model = "";
      if (Build.VERSION.SDK_INT >= 31) {
        vendor = Build.SOC_MANUFACTURER == null ? "" : Build.SOC_MANUFACTURER.toLowerCase(Locale.ROOT);
        model = Build.SOC_MODEL == null ? "" : Build.SOC_MODEL.toLowerCase(Locale.ROOT);
      }
      String hw = Build.HARDWARE == null ? "" : Build.HARDWARE.toLowerCase(Locale.ROOT);
      boolean mali = vendor.contains("mediatek") || vendor.contains("google") || vendor.contains("unisoc")
          || vendor.contains("spreadtrum") || model.startsWith("mt") || model.startsWith("tensor")
          || hw.startsWith("mt") || hw.contains("mediatek");
      if (!mali) return;
      getSharedPreferences("launch_renderer", MODE_PRIVATE).edit().putString("mode", "native").apply();
      Log.i(TAG, "Mali GPU: launcher renderer set to native");
    } catch (Throwable t) {
      Log.w(TAG, "renderer migration skipped", t);
    }
  }

  /**
   * The launcher lists custom drivers from <external files>/drivers/<name>/
   * (a .so plus meta.json) and copies the chosen one into its private folder
   * when the game starts. Put the bundled ones there, once per APK version.
   */
  private void installBundledDrivers() {
    try {
      File ext = getExternalFilesDir(null);
      if (ext == null) return;
      long apk = getPackageManager().getPackageInfo(getPackageName(), 0).lastUpdateTime;
      for (String name : BUNDLED_DRIVERS) {
        File dir = new File(ext, "drivers/" + name);
        File stamp = new File(dir, ".bundled");
        if (stamp.isFile() && stamp.lastModified() == apk) continue;
        if (!dir.isDirectory() && !dir.mkdirs()) continue;
        boolean ok = true;
        for (String f : DRIVER_FILES) {
          File out = new File(dir, f), tmp = new File(dir, f + ".tmp");
          InputStream in = getAssets().open("drivers/" + name + "/" + f);
          FileOutputStream o = new FileOutputStream(tmp);
          try {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
          } finally {
            in.close();
            o.close();
          }
          ok &= tmp.renameTo(out);
        }
        if (ok) {
          new FileOutputStream(stamp).close();
          stamp.setLastModified(apk);
        }
      }
    } catch (Throwable t) {
      Log.w(TAG, "bundled drivers not installed", t);
    }
  }

  // ------------------------------------------------------------ layout hook
  @Override
  public void onGlobalLayout() {
    super.onGlobalLayout();
    if (failed) return;
    try {
      View bar = (View) launcherField("mHomeBar");
      boolean want = bar != null && bar.getVisibility() == View.VISIBLE && bar.getParent() instanceof ViewGroup;
      if (want && home == null) createHome((ViewGroup) bar.getParent(), bar);
      if (home != null) {
        int v = want ? View.VISIBLE : View.GONE;
        if (home.getVisibility() != v) {
          home.setVisibility(v);
          if (want) refresh();
        }
      }
    } catch (Throwable t) {
      failed = true;
      Log.w(TAG, "home screen disabled", t);
      if (home != null) home.setVisibility(View.GONE);
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    if (home != null && home.getVisibility() == View.VISIBLE) refresh();
  }

  private Object launcherField(String name) throws Exception {
    Field f = NmpLauncherActivity.class.getDeclaredField(name);
    f.setAccessible(true);
    return f.get(this);
  }

  private void createHome(ViewGroup parent, View bar) {
    WebView w = new WebView(this);
    w.setBackgroundColor(0xFF0B0C0E);
    WebSettings s = w.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setAllowFileAccess(true);
    s.setMediaPlaybackRequiresUserGesture(true);
    w.setWebViewClient(new WebViewClient() {
      @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
        return route(r.getUrl().toString());
      }
      @Override public boolean shouldOverrideUrlLoading(WebView v, String url) {
        return route(url);
      }
      @Override public void onPageFinished(WebView v, String url) {
        refresh();
      }
    });
    w.setOverScrollMode(View.OVER_SCROLL_NEVER);
    w.loadUrl("file:///android_asset/home/index.html");
    // directly above the launcher's home bar; the MP hub (added later) stays on top
    parent.addView(w, parent.indexOfChild(bar) + 1,
        new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    home = w;
  }

  // ------------------------------------------------------------ actions
  private boolean route(String url) {
    if (url == null || !url.startsWith("nmp://")) return false;
    try {
      android.net.Uri u = android.net.Uri.parse(url);
      if (!"home".equals(u.getHost())) { handleHubUrl(url); return true; }
      String a = u.getPath() == null ? "" : u.getPath().replace("/", "");
      switch (a) {
        case "play": press(1001); break;
        case "mp": press(1002); break;
        case "settings": press(1003); break;
        case "files": press(1004); break;
        case "telegram": press(1005); break;
        case "mode": setMode(u.getQueryParameter("m")); break;
        default: break;
      }
    } catch (Throwable t) {
      Log.w(TAG, "action failed: " + url, t);
    }
    return true;
  }

  private void press(int id) {
    View v = new View(this);
    v.setId(id);
    onClick(v);
  }

  // ------------------------------------------------------------ graphics modes
  private static final String[][] MODES_FAST_GPU = {
      // name, frame limit, 3D resolution scale
      {"quality", "30", "1.0"},
      {"balance", "60", "0.75"},
      {"performance", "60", "0.60"},
  };
  /** Mali-G57 MC2 class (the launcher's Helio G99 profile caps output at 540p). */
  private static final String[][] MODES_WEAK_GPU = {
      {"quality", "30", "0.75"},
      {"balance", "30", "0.60"},
      {"performance", "40", "0.50"},
  };

  private String[][] modes() {
    return weakGpu() ? MODES_WEAK_GPU : MODES_FAST_GPU;
  }

  private boolean weakGpu() {
    File f = argsFile();
    if (f == null || !f.isFile()) return false;
    try {
      return new String(readAll(new FileInputStream(f)), StandardCharsets.UTF_8)
          .contains("--gta4_output_height_cap=540");
    } catch (Throwable t) {
      return false;
    }
  }

  private File argsFile() {
    File d = getExternalFilesDir(null);
    return d == null ? null : new File(d, "args.txt");
  }

  private void setMode(String m) {
    String[] mode = null;
    for (String[] x : modes()) if (x[0].equals(m)) mode = x;
    if (mode == null) return;
    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("mode", m).apply();
    if (!applyMode(mode)) {
      Toast.makeText(this, "Режим применится после первого запуска игры", Toast.LENGTH_SHORT).show();
    }
  }

  /** Rewrites the frame limit / resolution lines of args.txt (only if it exists). */
  private boolean applyMode(String[] mode) {
    File f = argsFile();
    if (f == null || !f.isFile()) return false;
    try {
      String text = new String(readAll(new FileInputStream(f)), StandardCharsets.UTF_8);
      StringBuilder out = new StringBuilder();
      for (String line : text.split("\n", -1)) {
        if (line.startsWith("--gta4_frame_limit=") || line.startsWith("--gta4_3d_resolution_scale=")
            || line.startsWith("# 4NMP graphics mode:")) continue;
        // co-op lines a previous co-op start left behind: a co-op start writes them again,
        // and this write makes args.txt "fresh", so they must not survive it
        if (line.startsWith("--gta4_multiplayer_backend=") || line.startsWith("--gta4_player_name=")
            || line.startsWith("--gta4_lan_ipv4=") || line.startsWith("#-gta4_skip_intro=true")) continue;
        out.append(line).append('\n');
      }
      while (out.length() > 1 && out.charAt(out.length() - 1) == '\n' && out.charAt(out.length() - 2) == '\n')
        out.setLength(out.length() - 1);
      out.append("# 4NMP graphics mode: ").append(mode[0]).append('\n')
         .append("--gta4_frame_limit=").append(mode[1]).append('\n')
         .append("--gta4_3d_resolution_scale=").append(mode[2]).append('\n');
      File tmp = new File(f.getPath() + ".tmp");
      FileOutputStream o = new FileOutputStream(tmp);
      o.write(out.toString().getBytes(StandardCharsets.UTF_8));
      o.close();
      return tmp.renameTo(f);
    } catch (Throwable t) {
      Log.w(TAG, "mode not written", t);
      return false;
    }
  }

  private String currentMode() {
    SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
    String wanted = p.getString("mode", null);
    String limit = null, scale = null;
    File f = argsFile();
    if (f != null && f.isFile()) {
      try {
        String text = new String(readAll(new FileInputStream(f)), StandardCharsets.UTF_8);
        for (String line : text.split("\n")) {
          if (line.startsWith("--gta4_frame_limit=")) limit = line.substring(19).trim();
          if (line.startsWith("--gta4_3d_resolution_scale=")) scale = line.substring(27).trim();
        }
      } catch (Throwable ignored) { }
    }
    // the mode whose frame limit and resolution are closest to args.txt
    String[][] table = modes();
    double sc = 0.75;
    try { if (scale != null) sc = Double.parseDouble(scale); } catch (NumberFormatException ignored) { }
    String actual = table[1][0];
    double best = Double.MAX_VALUE;
    for (String[] x : table) {
      double dist = Math.abs(Double.parseDouble(x[2]) - sc) + (x[1].equals(limit) ? 0 : 0.5);
      if (dist < best) { best = dist; actual = x[0]; }
    }
    // a mode chosen before args.txt existed: apply it now
    if (wanted != null && !wanted.equals(actual) && f != null && f.isFile()) {
      for (String[] x : table) if (x[0].equals(wanted) && applyMode(x)) return wanted;
    }
    return actual;
  }

  // ------------------------------------------------------------ data for the page
  private void refresh() {
    if (home == null) return;
    new Thread(new Runnable() {
      @Override public void run() {
        final String json = collect();
        runOnUiThread(new Runnable() {
          @Override public void run() {
            if (home != null) home.evaluateJavascript("window.nmpInit && nmpInit(" + json + ")", null);
          }
        });
      }
    }, "nmp-home").start();
  }

  private String collect() {
    StringBuilder j = new StringBuilder("{");
    try {
      PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
      String v = pi.versionName == null ? "" : pi.versionName.replace("-v504", "");
      j.append("\"version\":").append(q(v)).append(',');
    } catch (Throwable ignored) { }
    j.append("\"device\":").append(q(deviceName())).append(',');
    int thermal = 0;
    try {
      PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
      if (pm != null) thermal = pm.getCurrentThermalStatus();
    } catch (Throwable ignored) { }
    j.append("\"thermal\":").append(thermal).append(',');
    j.append("\"mode\":").append(q(currentMode())).append(',');
    j.append("\"modes\":[");
    String[][] table = modes();
    for (int i = 0; i < table.length; i++) {
      if (i > 0) j.append(',');
      j.append("{\"m\":").append(q(table[i][0])).append(",\"fps\":").append(table[i][1])
       .append(",\"scale\":").append(table[i][2]).append('}');
    }
    j.append("],");
    long played = 0;
    try {
      File pt = getExternalFilesDir(null) == null ? null : new File(getExternalFilesDir(null), "playtime.txt");
      if (pt != null && pt.isFile())
        played = Long.parseLong(new String(readAll(new FileInputStream(pt)), StandardCharsets.UTF_8).trim());
    } catch (Throwable ignored) { }
    j.append("\"playtime\":").append(played).append(',');
    String game = null;
    try { game = getSharedPreferences("cache_location", MODE_PRIVATE).getString("game", null); } catch (Throwable ignored) { }
    j.append("\"ready\":").append(game != null).append(',');
    j.append("\"save\":").append(saveJson(game));
    return j.append('}').toString();
  }

  private static String deviceName() {
    String soc = "";
    if (Build.VERSION.SDK_INT >= 31) soc = Build.SOC_MODEL == null ? "" : Build.SOC_MODEL.toUpperCase(Locale.ROOT);
    String[][] names = {
        {"SM8850", "Snapdragon 8 Elite Gen 5"}, {"SM8845", "Snapdragon 8 Gen 5"}, {"SM8750", "Snapdragon 8 Elite"},
        {"SM8735", "Snapdragon 8s Gen 4"}, {"SM8650", "Snapdragon 8 Gen 3"}, {"SM8635", "Snapdragon 8s Gen 3"},
        {"SM8550", "Snapdragon 8 Gen 2"}, {"SM8475", "Snapdragon 8+ Gen 1"}, {"SM8450", "Snapdragon 8 Gen 1"},
        {"SM7675", "Snapdragon 7+ Gen 3"}, {"SM7550", "Snapdragon 7 Gen 3"}, {"SM7475", "Snapdragon 7+ Gen 2"},
        {"MT6989", "Dimensity 9300"}, {"MT6991", "Dimensity 9400"}, {"MT6985", "Dimensity 9200"},
        {"MT6789", "Helio G99"}, {"MT6769", "Helio G85"}, {"MT6785", "Helio G95"},
    };
    for (String[] n : names) if (soc.startsWith(n[0])) return n[1];
    String model = Build.MODEL == null ? "Android" : Build.MODEL;
    String brand = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER;
    if (!brand.isEmpty() && !model.toLowerCase(Locale.ROOT).startsWith(brand.toLowerCase(Locale.ROOT)))
      model = brand.substring(0, 1).toUpperCase(Locale.ROOT) + brand.substring(1) + " " + model;
    return model;
  }

  /** Newest save of the portable save folder (<game folder>/../save/<XUID>/545407F2/00000001/<slot>/<slot>). */
  private static String saveJson(String game) {
    if (game == null) return "null";
    File root = new File(new File(game).getParentFile(), "save");
    File[] profiles = root.listFiles();
    if (profiles == null) return "null";
    File newest = null;
    HashSet<String> slots = new HashSet<>();
    for (File p : profiles) {
      if (!p.isDirectory() || !p.getName().matches("[0-9A-Fa-f]{16}")) continue;
      File[] dirs = new File(p, "545407F2/00000001").listFiles();
      if (dirs == null) continue;
      for (File d : dirs) {
        File data = new File(d, d.getName());
        if (!d.isDirectory() || !data.isFile()) continue;
        slots.add(d.getName());
        if (newest == null || data.lastModified() > newest.lastModified()) newest = data;
      }
    }
    if (newest == null) return "null";
    String name = newest.getName();
    String slot = name;
    if (name.matches("SGTA4\\d\\d")) slot = "№ " + Integer.parseInt(name.substring(5));
    StringBuilder j = new StringBuilder("{");
    j.append("\"slot\":").append(q(slot)).append(',');
    j.append("\"time\":").append(newest.lastModified()).append(',');
    j.append("\"count\":").append(slots.size());
    File thumb = new File(newest.getParentFile(), "__thumbnail.png");
    if (thumb.isFile() && thumb.length() < 512 * 1024) {
      try {
        String b64 = Base64.encodeToString(readAll(new FileInputStream(thumb)), Base64.NO_WRAP);
        j.append(",\"thumb\":").append(q("data:image/png;base64," + b64));
      } catch (Throwable ignored) { }
    }
    return j.append('}').toString();
  }

  // ------------------------------------------------------------ helpers
  private static byte[] readAll(InputStream in) throws java.io.IOException {
    try {
      ByteArrayOutputStream b = new ByteArrayOutputStream();
      byte[] buf = new byte[16384];
      int n;
      while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
      return b.toByteArray();
    } finally {
      in.close();
    }
  }

  private static String q(String s) {
    if (s == null) return "null";
    StringBuilder b = new StringBuilder("\"");
    for (char c : s.toCharArray()) {
      if (c == '"' || c == '\\') b.append('\\').append(c);
      else if (c < 0x20 || c == 0x2028 || c == 0x2029) b.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
      else b.append(c);
    }
    return b.append('"').toString();
  }
}
