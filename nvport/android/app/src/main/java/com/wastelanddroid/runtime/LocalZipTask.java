package com.wastelanddroid.runtime;

import android.webkit.WebView;
import com.wastelanddroid.MainActivity;

public class LocalZipTask implements Runnable {
    public String gameDir;
    public MainActivity main;
    public WebView wv;
    public String zipPath;

    public LocalZipTask(MainActivity mainActivity, WebView webView, String str, String str2) {
        this.main = mainActivity;
        this.wv = webView;
        this.gameDir = str;
        this.zipPath = str2;
    }

    @Override // java.lang.Runnable
    public void run() {
        try {
            String strInstallLocalZip = CoreModInstaller.installLocalZip(this.gameDir, this.zipPath);
            MainActivity mainActivity = this.main;
            WebView webView = this.wv;
            webView.post(new NexusDoneRunnable(mainActivity, webView, strInstallLocalZip));
        } catch (Throwable th) {
        }
    }
}
