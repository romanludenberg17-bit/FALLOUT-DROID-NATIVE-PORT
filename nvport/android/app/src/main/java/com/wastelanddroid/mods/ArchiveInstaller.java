package com.wastelanddroid.mods;

import com.wastelanddroid.game.DeviceProfile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;

/**
 * One installer for every mod archive (.zip and .7z): finds where the game's Data folder
 * starts inside the archive at any depth ("Mod/Main/meshes/..." included), runs a FOMOD
 * installer's default choices, refuses body and clothing replacers on chips that cannot
 * carry them, and copies the files into Data (keeping a backup of what it overwrites).
 */
public final class ArchiveInstaller {
    public static final String OK = "OK", ERR = "ERR", ERR_RAR = "ERR_RAR", ERR_HTML = "ERR_HTML",
            ERR_EMPTY = "ERR_EMPTY", ERR_REPLACER = "ERR_REPLACER", ERR_FORMAT = "ERR_FORMAT";

    public static final class Result {
        public String code = ERR;
        public String detail = "";
        public final List<String> files = new ArrayList<>();
        /** A body mesh came without the body's skin textures, none loose in Data either. */
        public boolean needsBodyTextures;
    }

    /** Body meshes installed with no body skin texture in Data (Type 6 needs Type3's). */
    static boolean missingBodyTextures(List<String> files, File dataDir) {
        boolean female = false, male = false;
        for (String f : files) {
            String l = f.replace('\\', '/').toLowerCase(Locale.ROOT);
            if (l.endsWith("femaleupperbody.nif")) female = true;
            else if (l.endsWith("/upperbody.nif") && l.contains("characters/_male")) male = true;
        }
        if (female && !new File(dataDir, "textures/characters/female/upperbodyfemale.dds").exists()
                && !com.wastelanddroid.util.Files2Exists.ci(dataDir, "textures/characters/female/upperbodyfemale.dds")) return true;
        return male && !new File(dataDir, "textures/characters/male/upperbodymale.dds").exists()
                && !com.wastelanddroid.util.Files2Exists.ci(dataDir, "textures/characters/male/upperbodymale.dds");
    }

    private interface Visitor {
        void entry(String name, InputStream in) throws IOException;
    }

    /** An archive read twice at most: names first, then the bytes in archive order. */
    private interface Archive {
        List<String> names() throws IOException;

        void each(Visitor v) throws IOException;
    }

    private static final String[] DATA_DIRS = {"meshes", "textures", "sound", "music", "menus", "shaders", "scripts",
            "strings", "video", "fonts", "lodsettings", "trees", "nvse", "fose", "config", "materials", "facegen"};

    static String kind(File f) throws IOException {
        byte[] b = new byte[6];
        int n;
        try (FileInputStream in = new FileInputStream(f)) {
            n = in.read(b);
        }
        if (n >= 2 && b[0] == 'P' && b[1] == 'K') return "zip";
        if (n >= 6 && b[0] == '7' && b[1] == 'z' && (b[2] & 0xff) == 0xBC && (b[3] & 0xff) == 0xAF) return "7z";
        if (n >= 3 && b[0] == 'R' && b[1] == 'a' && b[2] == 'r') return "rar";
        if (n >= 1 && (b[0] == '<' || b[0] == ' ' || b[0] == '\n')) return "html";
        return "?";
    }

    private static Archive zip(File f) {
        return new Archive() {
            public List<String> names() throws IOException {
                List<String> out = new ArrayList<>();
                try (ZipFile z = new ZipFile(f)) {
                    Enumeration<? extends ZipEntry> e = z.entries();
                    while (e.hasMoreElements()) {
                        ZipEntry ze = e.nextElement();
                        if (!ze.isDirectory()) out.add(ze.getName().replace('\\', '/'));
                    }
                }
                return out;
            }

            public void each(Visitor v) throws IOException {
                try (ZipFile z = new ZipFile(f)) {
                    Enumeration<? extends ZipEntry> e = z.entries();
                    while (e.hasMoreElements()) {
                        ZipEntry ze = e.nextElement();
                        if (ze.isDirectory()) continue;
                        try (InputStream in = z.getInputStream(ze)) {
                            v.entry(ze.getName().replace('\\', '/'), in);
                        }
                    }
                }
            }
        };
    }

    @SuppressWarnings("deprecation")
    private static Archive sevenZ(File f) {
        return new Archive() {
            public List<String> names() throws IOException {
                List<String> out = new ArrayList<>();
                try (SevenZFile z = new SevenZFile(f)) {
                    for (SevenZArchiveEntry e : z.getEntries()) {
                        if (!e.isDirectory() && e.hasStream()) out.add(e.getName().replace('\\', '/'));
                    }
                }
                return out;
            }

            public void each(Visitor v) throws IOException {
                try (SevenZFile z = new SevenZFile(f)) {
                    SevenZArchiveEntry e;
                    while ((e = z.getNextEntry()) != null) {
                        if (e.isDirectory() || !e.hasStream()) continue;
                        final SevenZFile zz = z;
                        v.entry(e.getName().replace('\\', '/'), new InputStream() {
                            public int read() throws IOException {
                                return zz.read();
                            }

                            public int read(byte[] b, int off, int len) throws IOException {
                                return zz.read(b, off, len);
                            }
                        });
                    }
                }
            }
        };
    }

    private static boolean dataLike(String component, boolean last) {
        String l = component.toLowerCase(Locale.ROOT);
        if (last) return l.endsWith(".esp") || l.endsWith(".esm") || l.endsWith(".bsa");
        for (String d : DATA_DIRS) if (l.equals(d)) return true;
        return false;
    }

    /** The archive folder that stands for Data: a "data/" folder, else the shallowest folder holding Data's own folders or plugins. */
    static String dataRoot(List<String> names) {
        String best = null;
        int bestDepth = Integer.MAX_VALUE;
        for (String n : names) {
            String low = n.toLowerCase(Locale.ROOT);
            if (low.contains("__macosx/") || low.contains("/fomod/") || low.startsWith("fomod/")) continue;
            String[] parts = n.split("/");
            for (int i = 0; i < parts.length; i++) {
                String p = parts[i].toLowerCase(Locale.ROOT);
                if (p.equals("data") && i < parts.length - 1) {
                    int depth = i + 1;
                    if (depth < bestDepth) {
                        bestDepth = depth;
                        best = join(parts, i + 1);
                    }
                    break;
                }
                if (dataLike(parts[i], i == parts.length - 1)) {
                    if (i < bestDepth) {
                        bestDepth = i;
                        best = join(parts, i);
                    }
                    break;
                }
            }
        }
        return best == null ? "" : best;
    }

    private static String join(String[] parts, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(parts[i]).append('/');
        return sb.toString();
    }

    /** Body, skeleton or worn-model files (meshes/textures of characters, armor, clothes). */
    public static boolean isBodyOrArmorReplacer(List<String> dataPaths) {
        for (String p : dataPaths) {
            String l = p.replace('\\', '/').toLowerCase(Locale.ROOT);
            if (l.startsWith("meshes/characters/") || l.startsWith("meshes/armor/") || l.startsWith("meshes/clothes/")
                    || l.startsWith("textures/characters/") || l.startsWith("textures/armor/") || l.startsWith("textures/clothes/")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Installs {@code archive} into {@code dataDir}. {@code backupDir} (may be null) keeps
     * the files it overwrites.
     */
    public static Result install(File archive, File dataDir, File backupDir) {
        Result r = new Result();
        try {
            String kind = kind(archive);
            Archive a;
            switch (kind) {
                case "zip": a = zip(archive); break;
                case "7z": a = sevenZ(archive); break;
                case "rar": r.code = ERR_RAR; return r;
                case "html": r.code = ERR_HTML; return r;
                default: r.code = ERR_FORMAT; return r;
            }
            List<String> names = a.names();
            if (names.isEmpty()) {
                r.code = ERR_EMPTY;
                return r;
            }
            // A FOMOD installer: its defaults.
            String fomodName = null;
            for (String n : names) {
                String l = n.toLowerCase(Locale.ROOT);
                if (l.equals("fomod/moduleconfig.xml") || l.endsWith("/fomod/moduleconfig.xml")) {
                    fomodName = n;
                    break;
                }
            }
            Map<String, List<String>> plan = null;
            if (fomodName != null) {
                final String want = fomodName;
                final byte[][] xml = new byte[1][];
                a.each((name, in) -> {
                    if (xml[0] == null && name.equals(want)) {
                        ByteArrayOutputStream bo = new ByteArrayOutputStream();
                        copy(in, bo);
                        xml[0] = bo.toByteArray();
                    }
                });
                try {
                    if (xml[0] != null) {
                        Fomod fm = Fomod.parse(xml[0]);
                        plan = fm.plan(names, fomodName.substring(0, fomodName.length() - "fomod/moduleconfig.xml".length()));
                        r.detail = "FOMOD: " + fm.chosen;
                        if (plan.isEmpty()) plan = null;
                    }
                } catch (Exception e) {
                    plan = null;
                }
            }
            if (plan == null) {
                plan = new HashMap<>();
                String root = dataRoot(names);
                for (String n : names) {
                    String l = n.toLowerCase(Locale.ROOT);
                    if (l.contains("__macosx/") || l.endsWith(".ds_store") || l.startsWith("fomod/") || l.contains("/fomod/")) continue;
                    String dest;
                    if (!root.isEmpty() && n.startsWith(root)) {
                        dest = n.substring(root.length());
                    } else if (root.isEmpty()) {
                        dest = n;
                    } else if (l.endsWith(".dll") && !l.contains("/")) {
                        dest = "NVSE/Plugins/" + n;
                    } else {
                        continue; // read-me files and the like beside the Data folder
                    }
                    if (!dest.isEmpty()) {
                        List<String> d = new ArrayList<>();
                        d.add(dest);
                        plan.put(n, d);
                    }
                }
            }
            List<String> dests = new ArrayList<>();
            for (List<String> d : plan.values()) dests.addAll(d);
            if (dests.isEmpty()) {
                r.code = ERR_EMPTY;
                return r;
            }
            if (isBodyOrArmorReplacer(dests) && !DeviceProfile.replacersAllowed()) {
                r.code = ERR_REPLACER;
                r.detail = DeviceProfile.chipName();
                return r;
            }
            final String base = dataDir.getCanonicalPath() + File.separator;
            final Map<String, List<String>> fplan = plan;
            a.each((name, in) -> {
                List<String> targets = fplan.get(name);
                if (targets == null) return;
                byte[] data = null;
                for (String t : targets) {
                    File out = new File(dataDir, t);
                    if (!out.getCanonicalPath().startsWith(base)) continue;
                    if (data == null) {
                        ByteArrayOutputStream bo = new ByteArrayOutputStream();
                        copy(in, bo);
                        data = bo.toByteArray();
                    }
                    if (backupDir != null && out.exists()) {
                        File bak = new File(backupDir, t);
                        if (!bak.exists()) {
                            File bp = bak.getParentFile();
                            if (bp != null) bp.mkdirs();
                            try (FileInputStream fi = new FileInputStream(out); FileOutputStream fo = new FileOutputStream(bak)) {
                                copy(fi, fo);
                            }
                        }
                    }
                    File parent = out.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try (FileOutputStream fo = new FileOutputStream(out)) {
                        fo.write(data);
                    }
                    if (!r.files.contains(t)) r.files.add(t);
                }
            });
            r.code = r.files.isEmpty() ? ERR_EMPTY : OK;
            if (!r.files.isEmpty()) r.needsBodyTextures = missingBodyTextures(r.files, dataDir);
            return r;
        } catch (Throwable t) {
            r.code = ERR;
            r.detail = String.valueOf(t);
            return r;
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }
}
