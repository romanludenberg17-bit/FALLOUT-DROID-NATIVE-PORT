package com.mafia2re.game;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Finding and checking the Mafia II folder the user copied from the PC. */
final class GameFiles {
    static final String EXE = "pc/mafia2.exe";
    /** What the port needs (the same list the original setup screen checks). */
    static final String[] REQUIRED = {EXE, "pc/APEX_release.dll", "pc/APEX_Clothing.dll", "pc/sds",
            "physx/PhysXCore_283.dll", "physx/NxCooking_283.dll"};

    private GameFiles() {
    }

    static boolean hasAllFilesAccess(Context c) {
        try {
            if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
            return c.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (RuntimeException e) {
            return false;   // some firmwares throw here instead of answering
        }
    }

    /** Case-insensitive path lookup: PC copies arrive as "PC/Mafia2.exe" just as often. */
    static File find(File root, String rel) {
        File cur = root;
        for (String part : rel.split("/")) {
            String[] names = cur == null ? null : cur.list();
            File next = null;
            if (names != null) {
                for (String n : names) {
                    if (n.equalsIgnoreCase(part)) {
                        next = new File(cur, n);
                        break;
                    }
                }
            }
            if (next == null) return null;
            cur = next;
        }
        return cur;
    }

    static List<String> missing(File root) {
        List<String> out = new ArrayList<>();
        for (String r : REQUIRED) if (find(root, r) == null) out.add(r);
        return out;
    }

    /**
     * People pick the wrong level all the time: the "pc" folder itself, or the folder that
     * contains "Mafia II". Walk to the folder that actually has pc/mafia2.exe.
     */
    static File normalize(File picked) {
        if (picked == null) return null;
        if (find(picked, EXE) != null) return picked;
        File parent = picked.getParentFile();
        if (parent != null && find(parent, EXE) != null) return parent;
        String[] kids = picked.list();
        if (kids != null) {
            for (String k : kids) {
                File sub = new File(picked, k);
                if (sub.isDirectory() && find(sub, EXE) != null) return sub;
            }
        }
        return picked;
    }

    /** Folder chosen in the system picker -> real path (works with "All files access"). */
    static File fromTreeUri(Uri tree) {
        try {
            String id = DocumentsContract.getTreeDocumentId(tree);
            int colon = id.indexOf(':');
            String vol = colon < 0 ? id : id.substring(0, colon);
            String rel = colon < 0 ? "" : id.substring(colon + 1);
            File base;
            if ("primary".equalsIgnoreCase(vol)) base = Environment.getExternalStorageDirectory();
            else if ("home".equalsIgnoreCase(vol)) base = new File(Environment.getExternalStorageDirectory(), "Documents");
            else base = new File("/storage/" + vol);
            return rel.isEmpty() ? base : new File(base, rel);
        } catch (Exception e) {
            return null;
        }
    }

    /** Where the port keeps saves: <externalFilesDir>/2K Games/Mafia II/Saves. */
    static File savesDir(Context c) {
        File d = c.getExternalFilesDir(null);
        return d == null ? null : new File(d, "2K Games/Mafia II/Saves");
    }

    /** {count, newest mtime} of save slots, ignoring the config files that live beside them. */
    static long[] saves(Context c) {
        long[] r = {0, 0};
        walk(savesDir(c), r, 0);
        return r;
    }

    private static void walk(File d, long[] r, int depth) {
        File[] fs = d == null || depth > 3 ? null : d.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) {
                walk(f, r, depth + 1);
                continue;
            }
            String n = f.getName().toLowerCase();
            if (n.endsWith(".cfg") || n.endsWith(".ini") || n.endsWith(".txt") || n.endsWith(".log")) continue;
            r[0]++;
            r[1] = Math.max(r[1], f.lastModified());
        }
    }

    /** Default locations offered before the user picks anything. */
    static File[] candidates(Context c) {
        File ext = c.getExternalFilesDir(null);
        File sd = Environment.getExternalStorageDirectory();
        return new File[]{
                ext == null ? null : new File(ext, "game"),
                new File(sd, "Mafia II"),
                new File(sd, "Mafia 2"),
                new File(sd, "Games/Mafia II"),
                new File(sd, "Download/Mafia II"),
        };
    }
}
