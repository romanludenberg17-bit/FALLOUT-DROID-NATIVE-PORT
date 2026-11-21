package com.wastelanddroid.game;

import com.wastelanddroid.util.Files2;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

public final class SaveBackup {
    private static final int KEEP = 5;

    private SaveBackup() {
    }

    /**
     * Copies the engine home's saves/ into file2/&lt;game&gt;/&lt;timestamp&gt;, keeping the last 5.
     * @param file the game's engine home (FalloutEngine.homeDir)
     */
    public static void backup(GameDef gameDef, File file, File file2) throws IOException {
        File file3 = new File(file, "saves");
        String[] list = file3.list();
        if (list == null || list.length == 0) {
            return;
        }
        String str = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        File file4 = new File(file2, gameDef.id);
        Files2.copyTree(file3, new File(file4, str));
        File[] fileArrListFiles = file4.listFiles(new FileFilter() { // from class: com.wastelanddroid.game.SaveBackup$$ExternalSyntheticLambda0
            @Override // java.io.FileFilter
            public final boolean accept(File file5) {
                return file5.isDirectory();
            }
        });
        if (fileArrListFiles == null || fileArrListFiles.length <= 5) {
            return;
        }
        Arrays.sort(fileArrListFiles, new Comparator() { // from class: com.wastelanddroid.game.SaveBackup$$ExternalSyntheticLambda1
            @Override // java.util.Comparator
            public final int compare(Object obj, Object obj2) {
                return ((File) obj).getName().compareTo(((File) obj2).getName());
            }
        });
        for (int i = 0; i < fileArrListFiles.length - 5; i++) {
            Files2.deleteTree(fileArrListFiles[i]);
        }
    }
}
