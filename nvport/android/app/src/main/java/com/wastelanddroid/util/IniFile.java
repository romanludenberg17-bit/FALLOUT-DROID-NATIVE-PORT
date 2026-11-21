package com.wastelanddroid.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class IniFile {
    private final List<String> lines = new ArrayList();

    public static IniFile load(File file) throws IOException {
        IniFile iniFile = new IniFile();
        if (file.exists()) {
            for (String str : new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1).split("\r?\n", -1)) {
                iniFile.lines.add(str);
            }
            while (!iniFile.lines.isEmpty()) {
                if (!iniFile.lines.get(iniFile.lines.size() - 1).isEmpty()) {
                    break;
                }
                iniFile.lines.remove(iniFile.lines.size() - 1);
            }
        }
        return iniFile;
    }

    public void save(File file) throws IOException {
        File parentFile = file.getParentFile();
        if (parentFile != null) {
            parentFile.mkdirs();
        }
        String str = String.join("\r\n", this.lines) + "\r\n";
        File file2 = new File(file.getPath() + ".tmp");
        Files.write(file2.toPath(), str.getBytes(StandardCharsets.ISO_8859_1), new OpenOption[0]);
        if (!file2.renameTo(file)) {
            throw new IOException("rename failed: " + file);
        }
    }

    private static boolean isSection(String str, String str2) {
        String strTrim = str.trim();
        return strTrim.startsWith("[") && strTrim.endsWith("]") && strTrim.substring(1, strTrim.length() - 1).trim().equalsIgnoreCase(str2);
    }

    private static String keyOf(String str) {
        int iIndexOf = str.indexOf(61);
        if (iIndexOf <= 0) {
            return null;
        }
        String strTrim = str.trim();
        if (strTrim.startsWith(";") || strTrim.startsWith("#")) {
            return null;
        }
        return str.substring(0, iIndexOf).trim().toLowerCase(Locale.ROOT);
    }

    public String get(String str, String str2) {
        boolean zIsSection = false;
        for (String str3 : this.lines) {
            if (str3.trim().startsWith("[")) {
                zIsSection = isSection(str3, str);
            } else if (zIsSection && str2.toLowerCase(Locale.ROOT).equals(keyOf(str3))) {
                return str3.substring(str3.indexOf(61) + 1).trim();
            }
        }
        return null;
    }

    public void set(String str, String str2, String str3) {
        String lowerCase = str2.toLowerCase(Locale.ROOT);
        int size = this.lines.size();
        int i = -1;
        int i2 = 0;
        while (true) {
            if (i2 >= this.lines.size()) {
                break;
            }
            if (this.lines.get(i2).trim().startsWith("[")) {
                if (i >= 0) {
                    size = i2;
                    break;
                } else if (isSection(this.lines.get(i2), str)) {
                    i = i2;
                }
            }
            i2++;
        }
        if (i >= 0) {
            for (int i3 = i + 1; i3 < size; i3++) {
                if (lowerCase.equals(keyOf(this.lines.get(i3)))) {
                    this.lines.set(i3, str2 + "=" + str3);
                    return;
                } else {
                    if (!this.lines.get(i3).trim().isEmpty()) {
                        i = i3;
                    }
                }
            }
            this.lines.add(i + 1, str2 + "=" + str3);
            return;
        }
        if (!this.lines.isEmpty()) {
            this.lines.add("");
        }
        this.lines.add("[" + str + "]");
        this.lines.add(str2 + "=" + str3);
    }
}
