package com.wastelanddroid.util;

import android.content.Context;
import android.os.Build;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.Thread;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class CrashLog {
    private static final String FILE = "fd-last-crash.txt";

    private CrashLog() {
    }

    public static void install(Context context) {
        final Context applicationContext = context.getApplicationContext();
        final Thread.UncaughtExceptionHandler defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() { // from class: com.wastelanddroid.util.CrashLog$$ExternalSyntheticLambda0
            @Override // java.lang.Thread.UncaughtExceptionHandler
            public final void uncaughtException(Thread thread, Throwable th) {
                CrashLog.lambda$install$0(applicationContext, defaultUncaughtExceptionHandler, thread, th);
            }
        });
    }

    static /* synthetic */ void lambda$install$0(Context context, Thread.UncaughtExceptionHandler uncaughtExceptionHandler, Thread thread, Throwable th) {
        try {
            write(context, "thread=" + thread.getName(), th);
            Prefs.get(context).edit().putBoolean(Prefs.AUTOSTART_BLOCKED, true).commit();
        } catch (Throwable unused) {
        }
        if (uncaughtExceptionHandler != null) {
            uncaughtExceptionHandler.uncaughtException(thread, th);
        }
    }

    public static void write(Context context, String str, Throwable th) {
        try {
            StringWriter stringWriter = new StringWriter();
            th.printStackTrace(new PrintWriter(stringWriter));
            Files.write(new File(context.getFilesDir(), FILE).toPath(), (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + "  " + str + "\nAndroid " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + "), " + Build.MANUFACTURER + " " + Build.MODEL + "\n\n" + stringWriter).getBytes(StandardCharsets.UTF_8), new OpenOption[0]);
        } catch (Throwable unused) {
        }
    }

    public static String takeUnseen(Context context) {
        File file = new File(context.getFilesDir(), FILE);
        if (!file.isFile()) {
            return null;
        }
        try {
            String str = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            File file2 = new File(context.getFilesDir(), "fd-last-crash.txt.seen");
            file2.delete();
            file.renameTo(file2);
            return str;
        } catch (Exception unused) {
            return null;
        }
    }
}
