package com.wastelanddroid.game;

import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import com.wastelanddroid.util.Files2;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class GameFiles {

    public static final class Report {
        public final List<String> errors = new ArrayList();
        public final List<String> warnings = new ArrayList();
        public final List<String> dlcs = new ArrayList();

        public boolean ok() {
            return this.errors.isEmpty();
        }
    }

    private GameFiles() {
    }

    public static File treeUriToFile(Uri uri) {
        File file;
        String treeDocumentId = DocumentsContract.getTreeDocumentId(uri);
        int iIndexOf = treeDocumentId.indexOf(58);
        String strSubstring = iIndexOf >= 0 ? treeDocumentId.substring(0, iIndexOf) : treeDocumentId;
        String strSubstring2 = iIndexOf >= 0 ? treeDocumentId.substring(iIndexOf + 1) : "";
        if ("primary".equalsIgnoreCase(strSubstring)) {
            file = Environment.getExternalStorageDirectory();
        } else {
            file = new File("/storage/" + strSubstring);
        }
        return strSubstring2.isEmpty() ? file : new File(file, strSubstring2);
    }

    public static Report validate(GameDef gameDef, File file) {
        Report report = new Report();
        if (file == null || !file.isDirectory() || !file.canRead()) {
            report.errors.add("Папка недоступна. Выдайте разрешение 'Доступ ко всем файлам'.");
            return report;
        }
        for (String str : gameDef.requiredFiles) {
            if (Files2.findCaseInsensitive(file, str) == null) {
                report.errors.add("Не найден: " + str);
            }
        }
        for (String str2 : gameDef.dlcs) {
            if (Files2.findCaseInsensitive(file, "Data/" + str2 + ".esm") != null) {
                if (Files2.findCaseInsensitive(file, "Data/" + str2 + " - Main.bsa") == null) {
                    report.warnings.add("DLC " + str2 + ": найден .esm, но нет '" + str2 + " - Main.bsa' — возможен вылет при загрузке.");
                }
                report.dlcs.add(str2);
            }
        }
        // Native engine (v2): the game's .exe, script extender, d3d9.dll and the LAA patch of the
        // Proton build are not used; only Data/ (masters, BSA archives, loose files) is read.
        if (!file.canWrite()) {
            report.warnings.add("Папка только для чтения: моды не смогут устанавливаться в Data/.");
        }
        return report;
    }
}
