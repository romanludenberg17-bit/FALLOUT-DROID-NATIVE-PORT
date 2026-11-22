package com.wastelanddroid.engine;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * "Waiting for the first frame": shown over the game's surface from the activity's start
 * (and a restart for a save) until the engine draws. One of the game's own loading
 * pictures, slowly zooming, its logo, a Pip-Boy style bar of running segments, the word
 * ЗАГРУЗКА with running dots and a tip that changes every few seconds; New Vegas in amber,
 * Fallout 3 in green. Fades out when the engine is up ({@link #finish}).
 */
public final class LoadingOverlay extends View {
    private static final String[] TIPS = {
            "Свайп «назад» — панель: цвет и прозрачность кнопок, лимит FPS, чувствительность",
            "Два пальца на экране — приблизить или отдалить камеру от третьего лица",
            "В Пип-Бое и меню: тап — выбор, свайп — прокрутка, щипок на карте — масштаб",
            "Кнопки сами прячутся в меню, диалогах и сюжетных сценах",
            "Первый запуск дольше обычного: движок собирает шейдеры под ваш GPU",
            "Сохранения можно загрузить прямо из лаунчера: «Сохранения» → «Загрузить»",
            "Настройки графики лаунчера действуют на обе игры",
    };

    private final boolean fo3;
    private final int accent;
    private final Paint img = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint shade = new Paint();
    private final Paint seg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scan = new Paint();
    private final RectF r = new RectF();
    private final Matrix m = new Matrix();
    private Bitmap picture, logo;
    private final long start = SystemClock.uptimeMillis();
    private long fadeFrom = -1;
    private final int tipOffset = new Random().nextInt(TIPS.length);

    public LoadingOverlay(Context c, boolean fo3) {
        super(c);
        this.fo3 = fo3;
        this.accent = fo3 ? 0xFF1AFF80 : 0xFFFFB840;
        setClickable(true); // nothing reaches the game under it
        // One picture per run of the activity: the window's background (still) and this
        // overlay show the same one.
        if (shared == null || sharedFo3 != fo3) {
            shared = pick(c, fo3 ? "Fallout3LoadingScreen" : "FalloutNVLoadingScreen");
            sharedFo3 = fo3;
        }
        picture = shared;
        logo = load(c, fo3 ? "art/logo_fo3.png" : "art/logo_fnv.png");
        Typeface mono;
        try {
            mono = Typeface.createFromAsset(c.getAssets(), "fonts/FalloutMonofonto.ttf");
        } catch (RuntimeException e) {
            mono = Typeface.MONOSPACE;
        }
        text.setTypeface(mono);
        text.setColor(accent);
        small.setTypeface(mono);
        small.setColor(0xFFE8E2CC);
        scan.setColor(0x22000000);
    }

    private static Bitmap shared;
    private static boolean sharedFo3;

    /**
     * The first frame of this screen as a picture, for the game window's background while
     * GameActivity loads the engine (before any view can be shown).
     */
    public static Bitmap still(Context c, boolean fo3, int w, int h) {
        LoadingOverlay o = new LoadingOverlay(c, fo3);
        o.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        o.layout(0, 0, w, h);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        o.draw(new Canvas(b));
        return b;
    }

    private static Bitmap load(Context c, String path) {
        try (InputStream in = c.getAssets().open(path)) {
            return BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            return null;
        }
    }

    private static Bitmap pick(Context c, String prefix) {
        try {
            String[] all = c.getAssets().list("loading");
            List<String> mine = new ArrayList<>();
            if (all != null) for (String n : all) if (n.startsWith(prefix)) mine.add(n);
            if (mine.isEmpty()) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 1;
            try (InputStream in = c.getAssets().open("loading/" + mine.get(new Random().nextInt(mine.size())))) {
                return BitmapFactory.decodeStream(in, null, o);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** The engine is up: fade out over half a second, then go. */
    public void finish() {
        if (fadeFrom < 0) {
            fadeFrom = SystemClock.uptimeMillis();
            invalidate();
        }
    }

    public boolean finishing() {
        return fadeFrom >= 0;
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        long now = SystemClock.uptimeMillis();
        float t = (now - start) / 1000f;
        float alpha = 1f;
        if (fadeFrom >= 0) {
            alpha = 1f - (now - fadeFrom) / 500f;
            if (alpha <= 0f) {
                setVisibility(GONE);
                if (getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) getParent()).removeView(this);
                return;
            }
        }
        int a255 = Math.round(255 * alpha);
        canvas.drawColor(Color.argb(a255, 0, 0, 0));
        // The picture: covering the screen, zooming in slowly (8 % over 20 s).
        if (picture != null) {
            float s = Math.max(w / (float) picture.getWidth(), h / (float) picture.getHeight());
            float z = s * (1f + 0.08f * Math.min(1f, t / 20f));
            m.reset();
            m.postScale(z, z);
            m.postTranslate((w - picture.getWidth() * z) / 2f, (h - picture.getHeight() * z) / 2f);
            img.setAlpha(a255);
            canvas.drawBitmap(picture, m, img);
        }
        // Darker towards the bottom, where the bar and the text are.
        shade.setShader(new LinearGradient(0, h * 0.35f, 0, h, 0x00000000, Color.argb(Math.round(225 * alpha), 0, 0, 0), Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, shade);
        // Scanlines.
        scan.setAlpha(Math.round(34 * alpha));
        float step = Math.max(3f, dp(3));
        for (float y = 0; y < h; y += step) canvas.drawRect(0, y, w, y + step / 2f, scan);
        // The logo, top left.
        if (logo != null) {
            float lh = h * 0.16f, lw = lh * logo.getWidth() / logo.getHeight();
            r.set(dp(28), dp(22), dp(28) + lw, dp(22) + lh);
            img.setAlpha(a255);
            canvas.drawBitmap(logo, null, r, img);
        }
        // The bar: 24 segments, a lit band of 6 running along it.
        float left = dp(28), right = w - dp(28), barY = h - dp(64), barH = dp(10);
        int n = 24;
        float gap = dp(3), segW = (right - left - gap * (n - 1)) / n;
        float head = (t * 14f) % (n + 6);
        for (int i = 0; i < n; i++) {
            float d = head - i;
            float lit = d >= 0 && d < 6 ? 1f - d / 6f : 0f;
            int ca = Math.round((40 + 215 * lit) * alpha);
            seg.setColor(Color.argb(ca, Color.red(accent), Color.green(accent), Color.blue(accent)));
            float x = left + i * (segW + gap);
            canvas.drawRect(x, barY, x + segW, barY + barH, seg);
        }
        // ЗАГРУЗКА with running dots, and the tip.
        text.setTextSize(dp(20));
        text.setAlpha(a255);
        int dots = (int) (t * 2.5f) % 4;
        canvas.drawText("ЗАГРУЗКА" + "....".substring(0, dots), left, barY - dp(14), text);
        small.setTextSize(dp(13));
        small.setAlpha(Math.round(220 * alpha));
        String tip = TIPS[(tipOffset + (int) (t / 5f)) % TIPS.length];
        canvas.drawText(tip, left, barY + barH + dp(24), small);
        String title = fo3 ? "FALLOUT 3" : "FALLOUT: NEW VEGAS";
        float tw = text.measureText(title);
        canvas.drawText(title, right - tw, barY - dp(14), text);
        postInvalidateOnAnimation();
    }
}
