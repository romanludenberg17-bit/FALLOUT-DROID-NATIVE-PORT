package com.wastelanddroid.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class Files2 {
    private Files2() {
    }

    public static void copy(File file, File file2) throws IOException {
        File parentFile = file2.getParentFile();
        if (parentFile != null) {
            parentFile.mkdirs();
        }
        FileInputStream fileInputStream = new FileInputStream(file);
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(file2);
            try {
                copy(fileInputStream, fileOutputStream);
                fileOutputStream.close();
                fileInputStream.close();
            } finally {
            }
        } catch (Throwable th) {
            try {
                fileInputStream.close();
            } catch (Throwable th2) {
                th.addSuppressed(th2);
            }
            throw th;
        }
    }

    public static long copy(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] bArr = new byte[65536];
        long j = 0;
        while (true) {
            int i = inputStream.read(bArr);
            if (i <= 0) {
                return j;
            }
            outputStream.write(bArr, 0, i);
            j += i;
        }
    }

    public static void copyTree(File file, File file2) throws IOException {
        if (file.isDirectory()) {
            file2.mkdirs();
            String[] list = file.list();
            if (list == null) {
                return;
            }
            for (String str : list) {
                copyTree(new File(file, str), new File(file2, str));
            }
            return;
        }
        copy(file, file2);
    }

    public static void deleteTree(File file) {
        File[] fileArrListFiles;
        if (file.isDirectory() && (fileArrListFiles = file.listFiles()) != null) {
            for (File file2 : fileArrListFiles) {
                deleteTree(file2);
            }
        }
        file.delete();
    }

    public static File findCaseInsensitive(File file, String str) {
        for (String str2 : str.split("/")) {
            File file2 = null;
            if (file == null || !file.isDirectory()) {
                return null;
            }
            File file3 = new File(file, str2);
            if (file3.exists()) {
                file = file3;
            } else {
                String[] list = file.list();
                if (list != null) {
                    int length = list.length;
                    int i = 0;
                    while (true) {
                        if (i >= length) {
                            break;
                        }
                        String str3 = list[i];
                        if (str3.equalsIgnoreCase(str2)) {
                            file2 = new File(file, str3);
                            break;
                        }
                        i++;
                    }
                }
                file = file2;
            }
        }
        return file;
    }
}
