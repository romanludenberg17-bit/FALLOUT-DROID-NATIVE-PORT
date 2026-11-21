package com.wastelanddroid.runtime;

import android.app.Dialog;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.WebView;
import com.wastelanddroid.MainActivity;

public class NexusDownloadListener implements DownloadListener {
    public Dialog dlg;
    public String gameDir;
    public MainActivity main;
    public WebView wv;

    public NexusDownloadListener(MainActivity mainActivity, WebView webView, Dialog dialog, String str) {
        this.main = mainActivity;
        this.wv = webView;
        this.dlg = dialog;
        this.gameDir = str;
    }

    @Override // android.webkit.DownloadListener
    public void onDownloadStart(String str, String str2, String str3, String str4, long j) {
        try {
            String cookie = CookieManager.getInstance().getCookie(str);
            this.dlg.dismiss();
            MainActivity mainActivity = this.main;
            WebView webView = this.wv;
            webView.evaluateJavascript("if(window.__fdExternalModStart)window.__fdExternalModStart();", null);
            NexusDownloadTask nexusDownloadTask = new NexusDownloadTask(mainActivity, webView, this.gameDir, str);
            nexusDownloadTask.cookie = cookie;
            nexusDownloadTask.ua = str2;
            new Thread(nexusDownloadTask).start();
        } catch (Throwable th) {
        }
    }
}
