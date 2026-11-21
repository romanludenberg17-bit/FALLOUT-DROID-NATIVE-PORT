package com.wastelanddroid.runtime;

import android.webkit.WebView;
import com.wastelanddroid.MainActivity;

public class NexusDownloadTask implements Runnable {
    public String cookie;
    public String gameDir;
    public MainActivity main;
    public String ua;
    public String url;
    public WebView wv;

    public NexusDownloadTask(MainActivity mainActivity, WebView webView, String str, String str2) {
        this.main = mainActivity;
        this.wv = webView;
        this.gameDir = str;
        this.url = str2;
    }

    @Override // java.lang.Runnable
    public void run() {
        try {
            String strDownloadAndInstallWithCookie = CoreModInstaller.downloadAndInstallWithCookie(this.gameDir, this.url, this.cookie, this.ua);
            MainActivity mainActivity = this.main;
            WebView webView = this.wv;
            webView.post(new NexusDoneRunnable(mainActivity, webView, strDownloadAndInstallWithCookie));
        } catch (Throwable th) {
        }
    }
}
