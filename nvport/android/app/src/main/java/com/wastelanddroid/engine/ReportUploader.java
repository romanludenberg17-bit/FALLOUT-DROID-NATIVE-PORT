package com.wastelanddroid.engine;

import android.content.Context;
import android.content.SharedPreferences;
import com.wastelanddroid.game.DeviceProfile;
import com.wastelanddroid.util.Prefs;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;
import org.json.JSONObject;

/**
 * Sends the developer the report of a run that went wrong, without the player doing
 * anything: after a crash of the game process (native crash, Java crash, out of memory,
 * ANR, lost GPU) or a run whose frame log shows low frame rates. The report
 * (FalloutEngine.lastSessionLogs, gzip) goes to a private GitHub repository through the
 * contents API, with a token limited to that repository (assets/report_upload.json, put
 * there by the build from the signing keys folder; without it nothing is sent). The
 * player can switch it off (pref "auto_reports"). At most 4 uploads a day.
 */
public final class ReportUploader {
    private ReportUploader() {}

    private static final Pattern FRAME = Pattern.compile("frame log: ([0-9.]+) fps over .*?>50 ms (\\d+) \\(screen (\\d+)\\)");
    private static final float LOW_FPS = 25f;

    public static void checkInBackground(Context context) {
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                check(app);
            } catch (Throwable t) {
                android.util.Log.w("FalloutDroid", "report upload", t);
            }
        }, "report upload").start();
    }

    private static JSONObject config(Context c) {
        try (InputStream in = c.getAssets().open("report_upload.json")) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            JSONObject o = new JSONObject(bo.toString("UTF-8"));
            return o.optString("token").isEmpty() || o.optString("repo").isEmpty() ? null : o;
        } catch (Exception e) {
            return null;
        }
    }

    static synchronized void check(Context c) throws Exception {
        SharedPreferences p = Prefs.get(c);
        if (!p.getBoolean("auto_reports", true)) return;
        JSONObject cfg = config(c);
        if (cfg == null) return;
        List<String> reasons = new ArrayList<>();
        // 1. The game process died since the last check.
        long seen = p.getLong("upload_exit_seen", 0), newest = seen;
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.app.ActivityManager am = (android.app.ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            String game = c.getPackageName() + FalloutEngine.GAME_PROCESS_SUFFIX;
            for (android.app.ApplicationExitInfo e : am.getHistoricalProcessExitReasons(c.getPackageName(), 0, 10)) {
                if (!game.equals(e.getProcessName()) || e.getTimestamp() <= seen) continue;
                newest = Math.max(newest, e.getTimestamp());
                switch (e.getReason()) {
                    case android.app.ApplicationExitInfo.REASON_CRASH_NATIVE: reasons.add("crash-native"); break;
                    case android.app.ApplicationExitInfo.REASON_CRASH: reasons.add("crash-java"); break;
                    case android.app.ApplicationExitInfo.REASON_LOW_MEMORY: reasons.add("oom"); break;
                    case android.app.ApplicationExitInfo.REASON_ANR: reasons.add("anr"); break;
                    default: break;
                }
            }
        }
        // 2. A new crash report or a low frame rate in the newest run log of each game.
        long logSeen = p.getLong("upload_log_seen", 0), logNewest = logSeen;
        for (String sc : new String[]{"fnv", "fo3"}) {
            File logs = new File(FalloutEngine.homeDir(c, sc), "logs");
            File[] files = logs.listFiles();
            if (files == null) continue;
            for (File f : files) {
                if (f.lastModified() <= logSeen) continue;
                String n = f.getName();
                if (n.startsWith("crash-") && n.endsWith(".txt")) {
                    reasons.add("crash-" + sc);
                    logNewest = Math.max(logNewest, f.lastModified());
                } else if (n.startsWith("nvforever-") && n.endsWith(".log")) {
                    logNewest = Math.max(logNewest, f.lastModified());
                    String low = lowFps(f);
                    if (low != null) reasons.add("lowfps-" + sc + "-" + low);
                }
            }
        }
        p.edit().putLong("upload_exit_seen", newest).putLong("upload_log_seen", logNewest).commit();
        // The first check after install only marks what is there.
        if (seen == 0 && logSeen == 0 && !p.getBoolean("upload_primed", false)) {
            p.edit().putBoolean("upload_primed", true).commit();
            return;
        }
        if (reasons.isEmpty()) return;
        String day = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new java.util.Date());
        int today = day.equals(p.getString("upload_day", "")) ? p.getInt("upload_count", 0) : 0;
        if (today >= 4) return;
        File report = FalloutEngine.lastSessionLogs(c);
        if (report == null || !report.isFile()) return;
        String reason = String.join("+", new java.util.LinkedHashSet<>(reasons));
        if (reason.length() > 60) reason = reason.substring(0, 60);
        upload(c, cfg, report, reason, day);
        p.edit().putString("upload_day", day).putInt("upload_count", today + 1).apply();
    }

    /** "17fps" when the run's play-screen frame log windows have a median under LOW_FPS. */
    static String lowFps(File log) {
        try {
            byte[] b = java.nio.file.Files.readAllBytes(log.toPath());
            Matcher m = FRAME.matcher(new String(b, StandardCharsets.UTF_8));
            List<Float> play = new ArrayList<>();
            while (m.find()) {
                if ("1".equals(m.group(3))) play.add(Float.parseFloat(m.group(1)));
            }
            if (play.size() < 3) return null;
            Collections.sort(play);
            float median = play.get(play.size() / 2);
            return median < LOW_FPS ? Math.round(median) + "fps" : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String safe(String s) {
        return s.replaceAll("[^A-Za-z0-9._+-]", "_");
    }

    static void upload(Context c, JSONObject cfg, File report, String reason, String day) throws Exception {
        byte[] raw = java.nio.file.Files.readAllBytes(report.toPath());
        ByteArrayOutputStream gz = new ByteArrayOutputStream();
        try (GZIPOutputStream z = new GZIPOutputStream(gz)) {
            z.write(raw);
        }
        String time = new java.text.SimpleDateFormat("HHmmss", Locale.ROOT).format(new java.util.Date());
        String name = time + "-" + safe(reason) + "-" + safe(DeviceProfile.chipName()) + "-" + safe(FalloutEngine.versionName(c))
                + "-" + Long.toHexString(Double.doubleToLongBits(Math.random())).substring(0, 6) + ".txt.gz";
        String path = "reports/" + day + "/" + name;
        JSONObject body = new JSONObject();
        body.put("message", "report " + reason + " (" + android.os.Build.MODEL + ", " + DeviceProfile.chipName() + ")");
        body.put("content", android.util.Base64.encodeToString(gz.toByteArray(), android.util.Base64.NO_WRAP));
        URL url = new URL("https://api.github.com/repos/" + cfg.getString("repo") + "/contents/" + path);
        HttpURLConnection h = (HttpURLConnection) url.openConnection();
        h.setRequestMethod("PUT");
        h.setConnectTimeout(20000);
        h.setReadTimeout(60000);
        h.setDoOutput(true);
        h.setRequestProperty("Authorization", "Bearer " + cfg.getString("token"));
        h.setRequestProperty("Accept", "application/vnd.github+json");
        h.setRequestProperty("User-Agent", "FalloutDroid-reports");
        h.setRequestProperty("Content-Type", "application/json");
        try (OutputStream o = h.getOutputStream()) {
            o.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = h.getResponseCode();
        android.util.Log.i("FalloutDroid", "report upload " + path + ": HTTP " + code);
        h.disconnect();
    }
}
