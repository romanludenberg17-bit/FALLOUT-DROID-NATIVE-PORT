package com.wastelanddroid.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

public final class Links {
    public static final String TELEGRAM_CHANNEL = "rommy_apps";
    public static final String TELEGRAM_URL = "https://t.me/rommy_apps";

    private Links() {
    }

    public static void openTelegram(Context context) {
        try {
            try {
                context.startActivity(new Intent("android.intent.action.VIEW", Uri.parse("tg://resolve?domain=rommy_apps")));
            } catch (ActivityNotFoundException unused) {
                context.startActivity(new Intent("android.intent.action.VIEW", Uri.parse(TELEGRAM_URL)));
            }
        } catch (ActivityNotFoundException unused2) {
            Toast.makeText(context, TELEGRAM_URL, 1).show();
        }
    }
}
