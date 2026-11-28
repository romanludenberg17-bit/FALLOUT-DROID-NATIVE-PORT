package com.mafia2re.game;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

/** The ReMaf2 Telegram channel (same as the other ports). */
final class Links {
    static final String TELEGRAM_CHANNEL = "rommy_apps";
    static final String TELEGRAM_URL = "https://t.me/rommy_apps";

    private Links() {
    }

    static void openTelegram(Context c) {
        try {
            try {
                c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=" + TELEGRAM_CHANNEL)));
            } catch (ActivityNotFoundException e) {
                c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_URL)));
            }
        } catch (ActivityNotFoundException e) {
            Toast.makeText(c, TELEGRAM_URL, Toast.LENGTH_LONG).show();
        }
    }
}
