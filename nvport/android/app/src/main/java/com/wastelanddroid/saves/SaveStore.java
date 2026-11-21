package com.wastelanddroid.saves;

import android.os.Environment;
import com.wastelanddroid.game.GameDef;
import com.wastelanddroid.saves.SaveStore;
import com.wastelanddroid.util.Files2;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class SaveStore {
    public final File dir;
    private final GameDef game;

    public static final class Save {
        public final List<File> companions = new ArrayList();
        public final File file;

        Save(File file) {
            this.file = file;
        }

        public String title() {
            return this.file.getName().replaceAll("(?i)\\.fos$", "");
        }

        public long size() {
            long length = this.file.length();
            Iterator<File> it = this.companions.iterator();
            while (it.hasNext()) {
                length += it.next().length();
            }
            return length;
        }
    }

    /** @param file the game's engine home (FalloutEngine.homeDir): saves are in its saves/. */
    public SaveStore(GameDef gameDef, File file) {
        this.game = gameDef;
        File file2 = new File(file, "saves");
        this.dir = file2;
        file2.mkdirs();
    }

    private static boolean isSave(String str) {
        // NVForever's own save format (not the original .fos).
        return str.toLowerCase(Locale.ROOT).endsWith(".nvfsave");
    }

    private static boolean isCompanion(String str) {
        String lowerCase = str.toLowerCase(Locale.ROOT);
        return lowerCase.endsWith(".nvfsave.bak");
    }

    public static boolean acceptsFile(String str) {
        return isSave(str) || isCompanion(str);
    }

    public List<Save> list() {
        int i;
        ArrayList arrayList = new ArrayList();
        File[] fileArrListFiles = this.dir.listFiles();
        if (fileArrListFiles == null) {
            return arrayList;
        }
        int length = fileArrListFiles.length;
        int i2 = 0;
        int i3 = 0;
        while (i3 < length) {
            File file = fileArrListFiles[i3];
            if (file.isFile() && isSave(file.getName())) {
                Save save = new Save(file);
                String strSubstring = file.getName().substring(i2, file.getName().length() - 4);
                int length2 = fileArrListFiles.length;
                int i4 = i2;
                while (i4 < length2) {
                    File file2 = fileArrListFiles[i4];
                    if (file2 == file || !isCompanion(file2.getName())) {
                        i = i4;
                    } else {
                        i = i4;
                        if (file2.getName().regionMatches(true, 0, strSubstring, 0, strSubstring.length())) {
                            save.companions.add(file2);
                        }
                    }
                    i4 = i + 1;
                }
                arrayList.add(save);
            }
            i3++;
            i2 = 0;
        }
        arrayList.sort(new Comparator() { // from class: com.wastelanddroid.saves.SaveStore$$ExternalSyntheticLambda0
            @Override // java.util.Comparator
            public final int compare(Object obj, Object obj2) {
                return Long.compare(((SaveStore.Save) obj2).file.lastModified(), ((SaveStore.Save) obj).file.lastModified());
            }
        });
        return arrayList;
    }

    public int importStream(String str, InputStream inputStream) throws IOException {
        if (str.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            return importZip(inputStream);
        }
        if (!acceptsFile(str)) {
            throw new IOException("'" + str + "' — не файл сохранения (.fos)");
        }
        writeAtomic(inputStream, new File(this.dir, new File(str).getName()));
        return isSave(str) ? 1 : 0;
    }

    private int importZip(InputStream inputStream) throws IOException {
        ZipInputStream zipInputStream = new ZipInputStream(inputStream);
        int i = 0;
        while (true) {
            try {
                ZipEntry nextEntry = zipInputStream.getNextEntry();
                if (nextEntry == null) {
                    break;
                }
                if (!nextEntry.isDirectory()) {
                    String name = new File(nextEntry.getName().replace('\\', '/')).getName();
                    if (acceptsFile(name)) {
                        writeAtomic(zipInputStream, new File(this.dir, name));
                        if (isSave(name)) {
                            i++;
                        }
                    }
                }
            } catch (Throwable th) {
                try {
                    zipInputStream.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
                throw th;
            }
        }
        zipInputStream.close();
        if (i != 0) {
            return i;
        }
        throw new IOException("В архиве нет файлов .fos");
    }

    private static void writeAtomic(InputStream inputStream, File file) throws IOException {
        File file2 = new File(file.getPath() + ".part");
        FileOutputStream fileOutputStream = new FileOutputStream(file2);
        try {
            Files2.copy(inputStream, fileOutputStream);
            fileOutputStream.close();
            if (file.exists() && !file.delete()) {
                throw new IOException("Не удалось заменить " + file.getName());
            }
            if (!file2.renameTo(file)) {
                throw new IOException("Не удалось сохранить " + file.getName());
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

    public File exportAll() throws IOException {
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "FalloutDroid/" + this.game.folder);
        file.mkdirs();
        File[] fileArrListFiles = this.dir.listFiles();
        if (fileArrListFiles != null) {
            for (File file2 : fileArrListFiles) {
                if (file2.isFile() && acceptsFile(file2.getName())) {
                    Files2.copy(file2, new File(file, file2.getName()));
                }
            }
        }
        return file;
    }

    public void delete(Save save) {
        save.file.delete();
        Iterator<File> it = save.companions.iterator();
        while (it.hasNext()) {
            it.next().delete();
        }
    }

    public static List<File> backups(File file, GameDef gameDef) {
        File[] fileArrListFiles = new File(file, gameDef.id).listFiles(new FileFilter() { // from class: com.wastelanddroid.saves.SaveStore$$ExternalSyntheticLambda1
            @Override // java.io.FileFilter
            public final boolean accept(File file2) {
                return file2.isDirectory();
            }
        });
        if (fileArrListFiles == null) {
            return new ArrayList();
        }
        ArrayList arrayList = new ArrayList(Arrays.asList(fileArrListFiles));
        arrayList.sort(new Comparator() { // from class: com.wastelanddroid.saves.SaveStore$$ExternalSyntheticLambda2
            @Override // java.util.Comparator
            public final int compare(Object obj, Object obj2) {
                return ((File) obj2).getName().compareTo(((File) obj).getName());
            }
        });
        return arrayList;
    }

    public void restoreBackup(File file) throws IOException {
        Files2.copyTree(file, this.dir);
    }
}
