package com.wastelanddroid.util;

import java.io.File;

/** A path under a folder, found whatever the case of each of its parts. */
public final class Files2Exists {
    private Files2Exists() {}

    public static boolean ci(File root, String rel) {
        File cur = root;
        for (String part : rel.split("/")) {
            File[] kids = cur.listFiles();
            if (kids == null) return false;
            File next = null;
            for (File k : kids) {
                if (k.getName().equalsIgnoreCase(part)) {
                    next = k;
                    break;
                }
            }
            if (next == null) return false;
            cur = next;
        }
        return cur.isFile();
    }
}
