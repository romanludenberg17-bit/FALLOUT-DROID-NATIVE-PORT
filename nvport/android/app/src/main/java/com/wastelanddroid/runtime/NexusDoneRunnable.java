package com.wastelanddroid.runtime;

import android.webkit.WebView;
import com.wastelanddroid.MainActivity;
import com.wastelanddroid.MainActivityTelegram;

public class NexusDoneRunnable implements Runnable {
    public MainActivity main;
    public String res;
    public WebView wv;

    public NexusDoneRunnable(MainActivity mainActivity, WebView webView, String str) {
        this.main = mainActivity;
        this.wv = webView;
        this.res = str;
    }

    @Override // java.lang.Runnable
    public void run() {
        try {
            MainActivity mainActivity = this.main;
            WebView webView = this.wv;
            webView.evaluateJavascript("if(window.__fdExternalModDone)window.__fdExternalModDone('" + this.res + "');", null);
            MainActivityTelegram.syncToJs(mainActivity, webView);
        } catch (Throwable th) {
        }
    }
}
