package com.wastelanddroid.runtime;

import android.app.Dialog;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.wastelanddroid.MainActivity;

public class NexusInAppBrowser {
    public static void open(MainActivity mainActivity, WebView webView, String str, String str2) {
        try {
            Dialog dialog = new Dialog(mainActivity, android.R.style.Theme_NoTitleBar_Fullscreen);
            LinearLayout linearLayout = new LinearLayout(mainActivity);
            linearLayout.setOrientation(1);
            linearLayout.setBackgroundColor(-16117492);
            LinearLayout linearLayout2 = new LinearLayout(mainActivity);
            linearLayout2.setOrientation(0);
            linearLayout2.setBackgroundColor(-15852782);
            linearLayout2.setPadding(24, 14, 24, 14);
            TextView textView = new TextView(mainActivity);
            textView.setText("NEXUS MODS — FALLOUT: NEW VEGAS");
            textView.setTextColor(-12976364);
            textView.setTextSize(14.0f);
            linearLayout2.addView(textView, new LinearLayout.LayoutParams(0, -2, 1.0f));
            Button button = new Button(mainActivity);
            button.setText("✕ Закрыть");
            button.setTextColor(-12976364);
            button.setBackgroundColor(-15060192);
            button.setOnClickListener(new NexusCloseClick(dialog));
            linearLayout2.addView(button);
            linearLayout.addView(linearLayout2);
            WebView webView2 = new WebView(mainActivity);
            WebSettings settings = webView2.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptCookie(true);
            cookieManager.setAcceptThirdPartyCookies(webView2, true);
            webView2.setWebViewClient(new WebViewClient());
            webView2.setDownloadListener(new NexusDownloadListener(mainActivity, webView, dialog, str));
            linearLayout.addView(webView2, new LinearLayout.LayoutParams(-1, 0, 1.0f));
            dialog.setContentView(linearLayout);
            webView2.loadUrl(str2);
            dialog.show();
        } catch (Throwable th) {
        }
    }

    public static void startDirectDownload(MainActivity mainActivity, WebView webView, String str, String str2) {
        try {
            new Thread(new NexusDownloadTask(mainActivity, webView, str, str2)).start();
        } catch (Throwable th) {
        }
    }

    public static void startLocalZipInstall(MainActivity mainActivity, WebView webView, String str, String str2) {
        try {
            new Thread(new LocalZipTask(mainActivity, webView, str, str2)).start();
        } catch (Throwable th) {
        }
    }
}
