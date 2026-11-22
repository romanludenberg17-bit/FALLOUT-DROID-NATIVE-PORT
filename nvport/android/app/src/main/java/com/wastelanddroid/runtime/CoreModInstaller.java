package com.wastelanddroid.runtime;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class CoreModInstaller {
    public static String check(String str) {
        if (str == null || str.length() == 0) {
            return "{\"noDir\":true}";
        }
        return "{\"4gb_patch\":" + is4gbPatched(str) + ",\"nvse\":" + new File(str, "nvse_1_4.dll").isFile() + ",\"jip_ln\":" + new File(str, "Data/NVSE/Plugins/jip_nvse.dll").isFile() + ",\"johnny_guitar\":" + new File(str, "Data/NVSE/Plugins/johnnyguitar.dll").isFile() + ",\"knvse\":" + new File(str, "Data/NVSE/Plugins/kNVSE.dll").isFile() + ",\"fps_overlay\":" + new File(str, "Data/FPSCounter.esp").isFile() + "}";
    }

    public static String downloadAndInstall(String str, String str2) {
        return downloadAndInstallWithCookie(str, str2, null, null);
    }

    public static String downloadAndInstallWithCookie(String str, String str2, String str3, String str4) {
        HttpURLConnection httpURLConnection;
        String headerField;
        if (str == null) {
            return "NO_DIR";
        }
        try {
            if (str.length() == 0) {
                return "NO_DIR";
            }
            int i = 0;
            while (true) {
                httpURLConnection = (HttpURLConnection) new URL(str2).openConnection();
                httpURLConnection.setInstanceFollowRedirects(false);
                httpURLConnection.setConnectTimeout(25000);
                httpURLConnection.setReadTimeout(120000);
                if (str4 == null) {
                    httpURLConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
                } else {
                    httpURLConnection.setRequestProperty("User-Agent", str4);
                }
                if (str3 != null && str3.length() != 0) {
                    httpURLConnection.setRequestProperty("Cookie", str3);
                }
                int responseCode = httpURLConnection.getResponseCode();
                if (responseCode < 300 || responseCode > 399 || i >= 5 || (headerField = httpURLConnection.getHeaderField("Location")) == null) {
                    break;
                }
                httpURLConnection.disconnect();
                str2 = headerField;
                i++;
            }
            return extractZipStream(str, httpURLConnection.getInputStream());
        } catch (Throwable th) {
            return "ERR";
        }
    }

    public static void ensurePlugin(String str, String str2) {
        try {
            String plugins = ModManager.readPlugins(str);
            if (plugins.contains(str2)) {
                return;
            }
            StringBuilder sb = new StringBuilder(plugins);
            if (plugins.length() != 0 && !plugins.endsWith("\n")) {
                sb.append("\r\n");
            }
            sb.append(str2);
            sb.append("\r\n");
            ModManager.savePlugins(str, sb.toString());
        } catch (Throwable th) {
        }
    }

    /** [nvport] Any mod archive (.zip/.7z) from a stream: spooled, then ArchiveInstaller. */
    public static String extractZipStream(String str, InputStream inputStream) {
        if (str == null || str.length() == 0) {
            return "NO_DIR";
        }
        File tmp = new File(str, ".fd_mod_import.tmp");
        try {
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = inputStream.read(buf)) > 0) out.write(buf, 0, n);
            } finally {
                inputStream.close();
            }
            return installArchive(str, tmp);
        } catch (Throwable th) {
            return "ERR";
        } finally {
            tmp.delete();
        }
    }

    /** Installs an archive file into the game's Data folder and enables its plugins. */
    public static String installArchive(String gameDir, File archive) {
        com.wastelanddroid.mods.ArchiveInstaller.Result r =
                com.wastelanddroid.mods.ArchiveInstaller.install(archive, new File(gameDir, "Data"), null);
        android.util.Log.i("FalloutDroid", "mod install: " + r.code + " " + r.detail + ", " + r.files.size() + " files");
        for (String f : r.files) {
            String l = f.toLowerCase();
            if (!f.contains("/") && (l.endsWith(".esp") || l.endsWith(".esm"))) {
                ensurePlugin(gameDir, f);
            }
        }
        return r.code;
    }

    public static String install(Context context, String str, String str2) {
        if (str == null || str.length() == 0) {
            return "{\"noDir\":true}";
        }
        boolean zEquals = str2.equals("all");
        if (zEquals || str2.equals("4gb_patch")) {
            patch4gb(str);
        }
        if (zEquals || str2.equals("nvse")) {
            unpackAssetZip(context, "core_mods/core_nvse.zip", str);
        }
        if (zEquals || str2.equals("jip_ln")) {
            unpackAssetZip(context, "core_mods/core_jip_ln.zip", str);
        }
        if (zEquals || str2.equals("johnny_guitar")) {
            unpackAssetZip(context, "core_mods/core_johnny_guitar.zip", str);
        }
        if (zEquals || str2.equals("knvse")) {
            unpackAssetZip(context, "core_mods/core_knvse.zip", str);
        }
        if (zEquals || str2.equals("fps_overlay")) {
            unpackAssetZip(context, "core_mods/core_fps_overlay.zip", str);
        }
        return check(str);
    }

    public static String installLocalZip(String str, String str2) {
        if (str == null) {
            return "NO_DIR";
        }
        try {
            return str.length() != 0 ? installArchive(str, new File(str2)) : "NO_DIR";
        } catch (Throwable th) {
            return "ERR";
        }
    }

    public static boolean is4gbPatched(String str) {
        if (str == null) {
            return false;
        }
        try {
            File file = new File(str, "FalloutNV.exe");
            if (!file.isFile()) {
                return false;
            }
            RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");
            randomAccessFile.seek(60L);
            randomAccessFile.seek((randomAccessFile.read() | (randomAccessFile.read() << 8) | (randomAccessFile.read() << 16) | (randomAccessFile.read() << 24)) + 22);
            int i = randomAccessFile.read() & 32;
            randomAccessFile.close();
            return i != 0;
        } catch (Throwable th) {
            return false;
        }
    }

    public static String listDirJson(String str) {
        try {
            if (!Environment.isExternalStorageManager()) {
                return "{\"noPerm\":true}";
            }
            if (str == null || str.length() == 0) {
                str = "/storage/emulated/0";
            }
            File file = new File(str);
            StringBuilder sb = new StringBuilder("{\"path\":\"");
            sb.append(file.getAbsolutePath().replace("\\", "\\\\").replace("\"", "\\\""));
            sb.append("\",\"hasFnv\":");
            sb.append(new File(file, "FalloutNV.exe").isFile());
            sb.append(",\"hasFo3\":");
            sb.append(new File(file, "Fallout3.exe").isFile());
            sb.append(",\"dirs\":[");
            File[] fileArrListFiles = file.listFiles();
            if (fileArrListFiles != null) {
                int i = 0;
                for (File file2 : fileArrListFiles) {
                    if (file2.isDirectory()) {
                        if (i != 0) {
                            sb.append(",");
                        }
                        String strReplace = file2.getName().replace("\\", "\\\\").replace("\"", "\\\"");
                        sb.append("\"");
                        sb.append(strReplace);
                        sb.append("\"");
                        i++;
                    }
                }
                sb.append("],\"zips\":[");
                int i2 = 0;
                for (File file3 : fileArrListFiles) {
                    if (file3.isFile()) {
                        String name = file3.getName();
                        if (name.toLowerCase().endsWith(".zip") || name.toLowerCase().endsWith(".7z")) {
                            if (i2 != 0) {
                                sb.append(",");
                            }
                            String strReplace2 = name.replace("\\", "\\\\").replace("\"", "\\\"");
                            sb.append("\"");
                            sb.append(strReplace2);
                            sb.append("\"");
                            i2++;
                        }
                    }
                }
            } else {
                sb.append("],\"zips\":[");
            }
            sb.append("]}");
            return sb.toString();
        } catch (Throwable th) {
            return "{\"err\":true}";
        }
    }

    public static String listPluginsJson(String str) {
        if (str == null) {
            return "{\"noDir\":true}";
        }
        try {
            if (str.length() == 0) {
                return "{\"noDir\":true}";
            }
            File file = new File(str, "Data");
            StringBuilder sb = new StringBuilder("{\"files\":[");
            File[] fileArrListFiles = file.listFiles();
            if (fileArrListFiles != null) {
                int i = 0;
                for (File file2 : fileArrListFiles) {
                    if (file2.isFile()) {
                        String name = file2.getName();
                        String lowerCase = name.toLowerCase();
                        if (lowerCase.endsWith(".esm") || lowerCase.endsWith(".esp")) {
                            if (i != 0) {
                                sb.append(",");
                            }
                            String strReplace = name.replace("\\", "\\\\").replace("\"", "\\\"");
                            sb.append("\"");
                            sb.append(strReplace);
                            sb.append("\"");
                            i++;
                        }
                    }
                }
            }
            sb.append("],\"plugins\":\"");
            String plugins = ModManager.readPlugins(str);
            if (plugins != null) {
                sb.append(plugins.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "").replace("\n", "\\n"));
            }
            sb.append("\"}");
            return sb.toString();
        } catch (Throwable th) {
            return "{\"err\":true}";
        }
    }

    public static boolean patch4gb(String str) {
        if (str == null) {
            return false;
        }
        try {
            File file = new File(str, "FalloutNV.exe");
            if (!file.isFile()) {
                return false;
            }
            RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
            randomAccessFile.seek(60L);
            long j = (randomAccessFile.read() | (randomAccessFile.read() << 8) | (randomAccessFile.read() << 16) | (randomAccessFile.read() << 24)) + 22;
            randomAccessFile.seek(j);
            int i = randomAccessFile.read();
            if ((i & 32) == 0) {
                randomAccessFile.seek(j);
                randomAccessFile.write(i | 32);
            }
            randomAccessFile.close();
            return true;
        } catch (Throwable th) {
            return false;
        }
    }

    public static void saveTouchCfg(Context context, String str) {
        if (context == null || str == null) {
            return;
        }
        try {
            String[] strArrSplit = str.split("_");
            if (strArrSplit.length >= 5) {
                int i = Integer.parseInt(strArrSplit[0]);
                int i2 = Integer.parseInt(strArrSplit[1]);
                int i3 = Integer.parseInt(strArrSplit[2]);
                int i4 = Integer.parseInt(strArrSplit[3]);
                int i5 = Integer.parseInt(strArrSplit[4]);
                SharedPreferences.Editor editorEdit = context.getSharedPreferences("stick", 0).edit();
                editorEdit.putBoolean("dynamic", i != 0);
                editorEdit.putInt("width", i2);
                editorEdit.putInt("height", i3);
                editorEdit.putBoolean("zoom", i4 != 0);
                editorEdit.putInt("step", i5);
                editorEdit.apply();
            }
        } catch (Throwable th) {
        }
    }

    public static boolean unpackAssetZip(Context context, String str, String str2) {
        try {
            ZipInputStream zipInputStream = new ZipInputStream(context.getAssets().open(str));
            byte[] bArr = new byte[16384];
            while (true) {
                ZipEntry nextEntry = zipInputStream.getNextEntry();
                if (nextEntry == null) {
                    zipInputStream.close();
                    return true;
                }
                if (!nextEntry.isDirectory()) {
                    File file = new File(str2, nextEntry.getName());
                    File parentFile = file.getParentFile();
                    if (parentFile != null) {
                        parentFile.mkdirs();
                    }
                    FileOutputStream fileOutputStream = new FileOutputStream(file);
                    while (true) {
                        int i = zipInputStream.read(bArr);
                        if (i <= 0) {
                            break;
                        }
                        fileOutputStream.write(bArr, 0, i);
                    }
                    fileOutputStream.close();
                    zipInputStream.closeEntry();
                }
            }
        } catch (Throwable th) {
            return false;
        }
    }
}
