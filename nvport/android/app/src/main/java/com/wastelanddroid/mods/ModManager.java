package com.wastelanddroid.mods;

import android.content.Context;
import com.wastelanddroid.game.GameDef;
import com.wastelanddroid.mods.ModManager;
import com.wastelanddroid.util.Files2;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class ModManager {
    private static final String[] DATA_DIRS = {"meshes", "textures", "sound", "music", "menus", "shaders", "scripts", "strings", "video", "fonts", "lodsettings", "trees", "nvse", "fose", "config"};
    private final File dataDir;
    private final GameDef game;
    private final File gameDir;
    private final File stateDir;

    public static final class Plugin {
        public boolean enabled;
        public final boolean master;
        public final String name;
        public final boolean official;

        Plugin(String str, boolean z, boolean z2) {
            this.name = str;
            this.enabled = z;
            this.official = z2;
            this.master = str.toLowerCase(Locale.ROOT).endsWith(".esm");
        }
    }

    public static final class InstalledMod {
        public final List<String> files = new ArrayList();
        public final String name;

        InstalledMod(String str) {
            this.name = str;
        }
    }

    public ModManager(Context context, GameDef gameDef, File file) {
        this.game = gameDef;
        this.gameDir = file;
        File fileFindCaseInsensitive = Files2.findCaseInsensitive(file, "Data");
        this.dataDir = fileFindCaseInsensitive == null ? new File(file, "Data") : fileFindCaseInsensitive;
        File file2 = new File(new File(context.getFilesDir(), "mods"), gameDef.id);
        this.stateDir = file2;
        file2.mkdirs();
    }

    private boolean isOfficial(String str) {
        String lowerCase = str.toLowerCase(Locale.ROOT);
        if (lowerCase.equals(this.game.exe.toLowerCase(Locale.ROOT).replace(".exe", ".esm"))) {
            return true;
        }
        for (String str2 : this.game.dlcs) {
            if (lowerCase.equals(str2.toLowerCase(Locale.ROOT) + ".esm")) {
                return true;
            }
        }
        return false;
    }

    private File orderFile() {
        return new File(this.stateDir, "loadorder.json");
    }

    public List<Plugin> loadPlugins() throws JSONException {
        ArrayList<String> arrayList = new ArrayList();
        String[] list = this.dataDir.list();
        if (list != null) {
            for (String str : list) {
                String lowerCase = str.toLowerCase(Locale.ROOT);
                if (lowerCase.endsWith(".esm") || lowerCase.endsWith(".esp")) {
                    arrayList.add(str);
                }
            }
        }
        ArrayList arrayList2 = new ArrayList();
        try {
            if (orderFile().exists()) {
                JSONArray jSONArray = new JSONArray(new String(Files.readAllBytes(orderFile().toPath()), StandardCharsets.UTF_8));
                for (int i = 0; i < jSONArray.length(); i++) {
                    JSONObject jSONObject = jSONArray.getJSONObject(i);
                    String strFindIgnoreCase = findIgnoreCase(arrayList, jSONObject.getString("name"));
                    if (strFindIgnoreCase != null) {
                        arrayList.remove(strFindIgnoreCase);
                        arrayList2.add(new Plugin(strFindIgnoreCase, jSONObject.optBoolean("enabled", true), isOfficial(strFindIgnoreCase)));
                    }
                }
            }
        } catch (Exception unused) {
        }
        Collections.sort(arrayList, new Comparator() { // from class: com.wastelanddroid.mods.ModManager$$ExternalSyntheticLambda3
            @Override // java.util.Comparator
            public final int compare(Object obj, Object obj2) {
                return ModManager.this.lambda$loadPlugins$0((String) obj, (String) obj2);
            }
        });
        for (String str2 : arrayList) {
            arrayList2.add(new Plugin(str2, true, isOfficial(str2)));
        }
        normalize(arrayList2);
        return arrayList2;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ int lambda$loadPlugins$0(String str, String str2) {
        return Long.compare(new File(this.dataDir, str).lastModified(), new File(this.dataDir, str2).lastModified());
    }

    private static String findIgnoreCase(List<String> list, String str) {
        for (String str2 : list) {
            if (str2.equalsIgnoreCase(str)) {
                return str2;
            }
        }
        return null;
    }

    public void normalize(List<Plugin> list) {
        ArrayList arrayList = new ArrayList();
        ArrayList arrayList2 = new ArrayList();
        ArrayList arrayList3 = new ArrayList();
        String strReplace = this.game.exe.toLowerCase(Locale.ROOT).replace(".exe", ".esm");
        Plugin plugin = null;
        for (Plugin plugin2 : list) {
            if (plugin2.name.equalsIgnoreCase(strReplace)) {
                plugin = plugin2;
            } else if (plugin2.official) {
                arrayList.add(plugin2);
            } else if (plugin2.master) {
                arrayList2.add(plugin2);
            } else {
                arrayList3.add(plugin2);
            }
        }
        arrayList.sort(new Comparator() { // from class: com.wastelanddroid.mods.ModManager$$ExternalSyntheticLambda0
            @Override // java.util.Comparator
            public final int compare(Object obj, Object obj2) {
                return ModManager.this.lambda$normalize$1((ModManager.Plugin) obj, (ModManager.Plugin) obj2);
            }
        });
        list.clear();
        if (plugin != null) {
            plugin.enabled = true;
            list.add(plugin);
        }
        list.addAll(arrayList);
        list.addAll(arrayList2);
        list.addAll(arrayList3);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ int lambda$normalize$1(Plugin plugin, Plugin plugin2) {
        return Integer.compare(dlcIndex(plugin.name), dlcIndex(plugin2.name));
    }

    private int dlcIndex(String str) {
        for (int i = 0; i < this.game.dlcs.length; i++) {
            if ((this.game.dlcs[i] + ".esm").equalsIgnoreCase(str)) {
                return i;
            }
        }
        return 99;
    }

    public void saveOrder(List<Plugin> list) throws IOException {
        normalize(list);
        try {
            JSONArray jSONArray = new JSONArray();
            for (Plugin plugin : list) {
                jSONArray.put(new JSONObject().put("name", plugin.name).put("enabled", plugin.enabled));
            }
            Files.write(orderFile().toPath(), jSONArray.toString(2).getBytes(StandardCharsets.UTF_8), new OpenOption[0]);
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    public int apply(File file) throws JSONException, IOException {
        List<Plugin> listLoadPlugins = loadPlugins();
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Plugin plugin : listLoadPlugins) {
            if (plugin.enabled) {
                int i2 = i + 1;
                if (i < 255) {
                    sb.append(plugin.name).append("\r\n");
                }
                i = i2;
            }
        }
        File file2 = new File(file, "drive_c/users/steamuser/AppData/Local/" + this.game.folder);
        file2.mkdirs();
        Files.write(new File(file2, "plugins.txt").toPath(), sb.toString().getBytes(StandardCharsets.ISO_8859_1), new OpenOption[0]);
        int i3 = 0;
        for (int i4 = 0; i4 < listLoadPlugins.size(); i4++) {
            if (!new File(this.dataDir, listLoadPlugins.get(i4).name).setLastModified(1199145600000L + (i4 * 60000L))) {
                i3++;
            }
        }
        return i3;
    }

    public int enabledCount(List<Plugin> list) {
        Iterator<Plugin> it = list.iterator();
        int i = 0;
        while (it.hasNext()) {
            if (it.next().enabled) {
                i++;
            }
        }
        return i;
    }

    private static boolean looksLikeData(String str) {
        String lowerCase = str.toLowerCase(Locale.ROOT);
        if (lowerCase.endsWith(".esp") || lowerCase.endsWith(".esm") || lowerCase.endsWith(".bsa")) {
            return true;
        }
        for (String str2 : DATA_DIRS) {
            if (lowerCase.equals(str2)) {
                return true;
            }
        }
        return false;
    }

    static String detectRoot(List<String> list) {
        for (String str : list) {
            String lowerCase = str.toLowerCase(Locale.ROOT);
            int iIndexOf = lowerCase.indexOf("data/");
            if (iIndexOf == 0 || (iIndexOf > 0 && lowerCase.charAt(iIndexOf - 1) == '/')) {
                return str.substring(0, iIndexOf + 5);
            }
        }
        Iterator<String> it = list.iterator();
        while (it.hasNext()) {
            String[] strArrSplit = it.next().split("/");
            if (strArrSplit.length >= 1 && looksLikeData(strArrSplit[0])) {
                return "";
            }
            if (strArrSplit.length >= 2 && looksLikeData(strArrSplit[1])) {
                return strArrSplit[0] + "/";
            }
        }
        return "";
    }

    public InstalledMod install(String str, InputStream inputStream, File file) throws IOException {
        File file2 = new File(file, "mod-import.zip");
        FileOutputStream fileOutputStream = new FileOutputStream(file2);
        try {
            Files2.copy(inputStream, fileOutputStream);
            fileOutputStream.close();
            try {
                ArrayList arrayList = new ArrayList();
                ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(file2.toPath(), new OpenOption[0]));
                while (true) {
                    try {
                        ZipEntry nextEntry = zipInputStream.getNextEntry();
                        if (nextEntry == null) {
                            break;
                        }
                        if (!nextEntry.isDirectory()) {
                            arrayList.add(nextEntry.getName().replace('\\', '/'));
                        }
                    } finally {
                    }
                }
                zipInputStream.close();
                if (arrayList.isEmpty()) {
                    throw new IOException("Архив пуст");
                }
                String strDetectRoot = detectRoot(arrayList);
                String strReplaceAll = str.replaceAll("[^\\p{L}\\p{N} ._-]", "_");
                InstalledMod installedMod = new InstalledMod(strReplaceAll);
                File file3 = new File(this.stateDir, strReplaceAll + ".bak");
                String str2 = this.dataDir.getCanonicalPath() + File.separator;
                zipInputStream = new ZipInputStream(Files.newInputStream(file2.toPath(), new OpenOption[0]));
                while (true) {
                    try {
                        ZipEntry nextEntry2 = zipInputStream.getNextEntry();
                        if (nextEntry2 == null) {
                            break;
                        }
                        if (!nextEntry2.isDirectory()) {
                            String strReplace = nextEntry2.getName().replace('\\', '/');
                            if (strReplace.startsWith(strDetectRoot)) {
                                String strSubstring = strReplace.substring(strDetectRoot.length());
                                if (!strSubstring.isEmpty() && !strSubstring.toLowerCase(Locale.ROOT).startsWith("fomod/")) {
                                    File file4 = new File(this.dataDir, strSubstring);
                                    if (file4.getCanonicalPath().startsWith(str2)) {
                                        if (file4.exists()) {
                                            File file5 = new File(file3, strSubstring);
                                            if (!file5.exists()) {
                                                Files2.copy(file4, file5);
                                            }
                                        }
                                        File parentFile = file4.getParentFile();
                                        if (parentFile != null) {
                                            parentFile.mkdirs();
                                        }
                                        FileOutputStream fileOutputStream2 = new FileOutputStream(file4);
                                        try {
                                            Files2.copy(zipInputStream, fileOutputStream2);
                                            fileOutputStream2.close();
                                            installedMod.files.add(strSubstring);
                                        } finally {
                                        }
                                    }
                                }
                            }
                        }
                    } finally {
                    }
                }
                zipInputStream.close();
                if (installedMod.files.isEmpty()) {
                    throw new IOException("В архиве не найдено файлов для папки Data");
                }
                saveMod(installedMod);
                return installedMod;
            } finally {
                file2.delete();
            }
        } catch (Throwable th) {
            try {
                fileOutputStream.close();
            } catch (Throwable th2) {
                th.addSuppressed(th2);
            }
            throw th;
        }
    }

    private void saveMod(InstalledMod installedMod) throws IOException {
        try {
            JSONArray jSONArray = new JSONArray();
            Iterator<String> it = installedMod.files.iterator();
            while (it.hasNext()) {
                jSONArray.put(it.next());
            }
            Files.write(new File(this.stateDir, installedMod.name + ".mod.json").toPath(), new JSONObject().put("name", installedMod.name).put("files", jSONArray).toString().getBytes(StandardCharsets.UTF_8), new OpenOption[0]);
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    public List<InstalledMod> installedMods() throws JSONException {
        ArrayList arrayList = new ArrayList();
        File[] fileArrListFiles = this.stateDir.listFiles(new FilenameFilter() { // from class: com.wastelanddroid.mods.ModManager$$ExternalSyntheticLambda1
            @Override // java.io.FilenameFilter
            public final boolean accept(File file, String str) {
                return str.endsWith(".mod.json");
            }
        });
        if (fileArrListFiles == null) {
            return arrayList;
        }
        for (File file : fileArrListFiles) {
            try {
                JSONObject jSONObject = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
                InstalledMod installedMod = new InstalledMod(jSONObject.getString("name"));
                JSONArray jSONArray = jSONObject.getJSONArray("files");
                for (int i = 0; i < jSONArray.length(); i++) {
                    installedMod.files.add(jSONArray.getString(i));
                }
                arrayList.add(installedMod);
            } catch (Exception unused) {
            }
        }
        arrayList.sort(new Comparator() { // from class: com.wastelanddroid.mods.ModManager$$ExternalSyntheticLambda2
            @Override // java.util.Comparator
            public final int compare(Object obj, Object obj2) {
                return ((ModManager.InstalledMod) obj).name.compareToIgnoreCase(((ModManager.InstalledMod) obj2).name);
            }
        });
        return arrayList;
    }

    public void uninstall(InstalledMod installedMod) throws IOException {
        File file = new File(this.stateDir, installedMod.name + ".bak");
        for (String str : installedMod.files) {
            File file2 = new File(this.dataDir, str);
            File file3 = new File(file, str);
            if (file3.exists()) {
                Files2.copy(file3, file2);
            } else {
                file2.delete();
            }
        }
        Files2.deleteTree(file);
        new File(this.stateDir, installedMod.name + ".mod.json").delete();
    }
}
