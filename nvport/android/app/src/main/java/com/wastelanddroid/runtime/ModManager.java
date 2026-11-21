package com.wastelanddroid.runtime;

import android.content.Context;
import android.content.res.AssetManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

public class ModManager {
    public static void copyAsset(AssetManager assetManager, File file, String str, String str2) {
        try {
            File file2 = new File(file, str2);
            File parentFile = file2.getParentFile();
            if (parentFile != null) {
                parentFile.mkdirs();
            }
            InputStream inputStreamOpen = assetManager.open(str);
            FileOutputStream fileOutputStream = new FileOutputStream(file2);
            byte[] bArr = new byte[16384];
            while (true) {
                int i = inputStreamOpen.read(bArr);
                if (i <= 0) {
                    fileOutputStream.close();
                    inputStreamOpen.close();
                    return;
                }
                fileOutputStream.write(bArr, 0, i);
            }
        } catch (Throwable unused) {
        }
    }

    public static String listMods(String str) {
        File[] fileArrListFiles;
        if (str == null) {
            return "";
        }
        try {
            File file = new File(str, "Data");
            if (!file.exists() || !file.isDirectory() || (fileArrListFiles = file.listFiles()) == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (File file2 : fileArrListFiles) {
                if (file2.isFile()) {
                    String name = file2.getName();
                    String lowerCase = name.toLowerCase();
                    if (lowerCase.endsWith(".esm") || lowerCase.endsWith(".esp")) {
                        sb.append(name);
                        sb.append("\n");
                    }
                }
            }
            return sb.toString();
        } catch (Throwable unused) {
            return "";
        }
    }

    public static String readPlugins(String str) {
        int length;
        if (str == null) {
            return "";
        }
        try {
            File file = new File(new File(str, "Data"), "plugins.txt");
            if (!file.exists() || (length = (int) file.length()) <= 0 || length > 262144) {
                return "";
            }
            byte[] bArr = new byte[length];
            FileInputStream fileInputStream = new FileInputStream(file);
            int i = fileInputStream.read(bArr);
            fileInputStream.close();
            return i > 0 ? new String(bArr, 0, i) : "";
        } catch (Throwable unused) {
            return "";
        }
    }

    public static void savePlugins(String str, String str2) {
        if (str == null || str2 == null) {
            return;
        }
        try {
            String strReplace = str2.replace("\\r", "\r").replace("\\n", "\n");
            File file = new File(new File(str, "Data"), "plugins.txt");
            byte[] bytes = strReplace.getBytes();
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            fileOutputStream.write(bytes);
            fileOutputStream.close();
        } catch (Throwable unused) {
        }
    }
}
