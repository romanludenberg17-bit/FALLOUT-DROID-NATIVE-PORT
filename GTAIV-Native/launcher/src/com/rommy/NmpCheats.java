package com.rommy;

import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;

/**
 * In-game cheats: a small "ЧИТЫ" button over the game and a mini menu. A cheat
 * is applied the way the console game expects it: a virtual Xbox pad (SDL)
 * opens Niko's phone (D-pad up), the keypad (up again), dials the number
 * (cursor on the 3x4 grid + A), calls (A) and puts the phone away (B).
 *
 * Loaded into the game process by libq.so (JNI) once libmain is up:
 * {@link #install(Application)}.
 */
public final class NmpCheats implements Application.ActivityLifecycleCallbacks {
  private static final String TAG = "NmpCheats";
  private static final String GAME_ACTIVITY = "com.libertyrecomp.LibertyActivity";
  private static final int PAD_ID = 0x4E4D50;  // "NMP"
  private static NmpCheats instance;

  static final String[][] CHEATS = {
      {"4825550100", "Здоровье, броня, патроны"}, {"3625550100", "Броня"},
      {"4865550150", "Оружие (набор 1)"}, {"4865550100", "Оружие (набор 2)"},
      {"2675550100", "Снять розыск"}, {"2675550150", "Повысить розыск"},
      {"4685550100", "Сменить погоду"}, {"9485550100", "Название песни"},
      {"3595550100", "Вертолёт Annihilator"}, {"9385550100", "Катер Jetmax"},
      {"2275550100", "FIB Buffalo"}, {"2275550175", "Comet"},
      {"2275550147", "Turismo"}, {"2275550168", "Super GT"},
      {"2275550142", "Cognoscenti"}, {"6255550100", "Мотоцикл NRG-900"},
      {"6255550150", "Мотоцикл Sanchez"},
  };

  private final Handler ui = new Handler(Looper.getMainLooper());
  private Activity game;
  private FrameLayout overlay;
  private View menu;
  private volatile boolean busy;

  /** Called by libq.so from a native thread. */
  public static void install(final Application app) {
    new Handler(Looper.getMainLooper()).post(new Runnable() {
      @Override public void run() {
        if (instance != null) return;
        instance = new NmpCheats();
        app.registerActivityLifecycleCallbacks(instance);
        Activity current = currentGameActivity();
        if (current != null) instance.attach(current);
      }
    });
  }

  /** The game activity if it is already resumed (we are loaded from its onCreate). */
  private static Activity currentGameActivity() {
    try {
      Class<?> at = Class.forName("android.app.ActivityThread");
      Object thread = at.getMethod("currentActivityThread").invoke(null);
      java.lang.reflect.Field f = at.getDeclaredField("mActivities");
      f.setAccessible(true);
      Object map = f.get(thread);
      for (Object record : ((java.util.Map<?, ?>) map).values()) {
        java.lang.reflect.Field af = record.getClass().getDeclaredField("activity");
        af.setAccessible(true);
        Activity a = (Activity) af.get(record);
        if (a != null && isGame(a)) return a;
      }
    } catch (Throwable t) {
      Log.w(TAG, "no current activity", t);
    }
    return null;
  }

  private static boolean isGame(Activity a) {
    for (Class<?> c = a.getClass(); c != null; c = c.getSuperclass())
      if (GAME_ACTIVITY.equals(c.getName())) return true;
    return false;
  }

  // ------------------------------------------------------------ overlay
  private int dp(float v) {
    return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
        game.getResources().getDisplayMetrics()));
  }

  private void attach(Activity a) {
    if (game == a && overlay != null && overlay.getParent() != null) return;
    detach();
    game = a;
    try {
      overlay = new FrameLayout(a);
      TextView button = new TextView(a);
      button.setText("ЧИТЫ");
      button.setTextColor(0xFFF5A623);
      button.setTypeface(Typeface.DEFAULT_BOLD);
      button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
      button.setGravity(Gravity.CENTER);
      GradientDrawable bg = new GradientDrawable();
      bg.setShape(GradientDrawable.OVAL);
      bg.setColor(0x99000000);
      bg.setStroke(dp(1.5f), 0xAAD0D0D0);
      button.setBackground(bg);
      FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(48), dp(48),
          Gravity.START | Gravity.CENTER_VERTICAL);
      lp.leftMargin = dp(10);
      lp.topMargin = -dp(40);
      overlay.addView(button, lp);
      button.setOnClickListener(new View.OnClickListener() {
        @Override public void onClick(View v) { toggleMenu(); }
      });
      a.addContentView(overlay, new ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      // The overlay itself must not eat the game's touches.
      overlay.setClickable(false);
      overlay.setFocusable(false);
    } catch (Throwable t) {
      Log.w(TAG, "overlay failed", t);
      overlay = null;
    }
  }

  private void detach() {
    if (overlay != null && overlay.getParent() instanceof ViewGroup)
      ((ViewGroup) overlay.getParent()).removeView(overlay);
    overlay = null;
    menu = null;
  }

  private void toggleMenu() {
    if (menu != null) { closeMenu(); return; }
    Activity a = game;
    LinearLayout box = new LinearLayout(a);
    box.setOrientation(LinearLayout.VERTICAL);
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(0xE6121316);
    bg.setCornerRadius(dp(12));
    bg.setStroke(1, 0x33FFFFFF);
    box.setBackground(bg);
    box.setPadding(dp(14), dp(10), dp(14), dp(10));
    TextView title = new TextView(a);
    title.setText(busy ? "ЧИТЫ · вводится…" : "ЧИТЫ");
    title.setTextColor(0xFFF5A623);
    title.setTypeface(Typeface.DEFAULT_BOLD);
    title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
    title.setPadding(0, 0, 0, dp(6));
    box.addView(title);
    LinearLayout list = new LinearLayout(a);
    list.setOrientation(LinearLayout.VERTICAL);
    for (final String[] c : CHEATS) {
      TextView row = new TextView(a);
      row.setText(c[1]);
      row.setTextColor(Color.WHITE);
      row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
      row.setPadding(0, dp(7), 0, dp(7));
      row.setOnClickListener(new View.OnClickListener() {
        @Override public void onClick(View v) { closeMenu(); apply(c[0], c[1]); }
      });
      list.addView(row);
    }
    ScrollView scroll = new ScrollView(a);
    scroll.addView(list);
    box.addView(scroll, new LinearLayout.LayoutParams(dp(220),
        Math.min(dp(260), a.getResources().getDisplayMetrics().heightPixels - dp(80))));
    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        Gravity.START | Gravity.CENTER_VERTICAL);
    lp.leftMargin = dp(66);
    overlay.addView(box, lp);
    menu = box;
  }

  private void closeMenu() {
    if (menu != null && overlay != null) overlay.removeView(menu);
    menu = null;
  }

  // ------------------------------------------------------------ virtual pad
  private static final int UP = KeyEvent.KEYCODE_DPAD_UP, DOWN = KeyEvent.KEYCODE_DPAD_DOWN,
      LEFT = KeyEvent.KEYCODE_DPAD_LEFT, RIGHT = KeyEvent.KEYCODE_DPAD_RIGHT,
      A = KeyEvent.KEYCODE_BUTTON_A, B = KeyEvent.KEYCODE_BUTTON_B;

  private void apply(final String number, final String name) {
    if (busy) return;
    busy = true;
    Toast.makeText(game, "Чит: " + name, Toast.LENGTH_SHORT).show();
    new Thread(new Runnable() {
      @Override public void run() {
        try {
          dial(number);
        } catch (Throwable t) {
          Log.w(TAG, "cheat input failed", t);
        } finally {
          busy = false;
        }
      }
    }, "nmp-cheat").start();
  }

  private Method padDown, padUp;
  private Class<?> sdl;

  private void dial(String number) throws Exception {
    sdl = Class.forName("org.libsdl.app.SDLControllerManager");
    Method add = null, remove = null;
    for (Method m : sdl.getDeclaredMethods()) {
      if (m.getName().equals("nativeAddJoystick")) add = m;
      if (m.getName().equals("nativeRemoveJoystick")) remove = m;
      if (m.getName().equals("onNativePadDown")) padDown = m;
      if (m.getName().equals("onNativePadUp")) padUp = m;
    }
    if (add == null || padDown == null || padUp == null) throw new IllegalStateException("SDL pad API missing");
    add.setAccessible(true); padDown.setAccessible(true); padUp.setAccessible(true);
    Object[] args = new Object[add.getParameterTypes().length];
    Class<?>[] types = add.getParameterTypes();
    int ints = 0;
    for (int i = 0; i < args.length; i++) {
      if (types[i] == String.class) args[i] = i == 1 ? "4NMP Cheats" : "4nmp-cheats";
      else if (types[i] == boolean.class) args[i] = false;
      else {
        // device id, vendor (Microsoft), product (Xbox 360 pad), button mask, axes, axis mask, hats
        int[] v = {PAD_ID, 0x045E, 0x028E, 0x7FFF, 0, 0, 0};
        args[i] = ints < v.length ? v[ints] : 0;
        ints++;
      }
    }
    add.invoke(null, args);
    try {
      Thread.sleep(400);
      press(UP, 900);        // phone
      press(UP, 800);        // keypad
      int x = 0, y = 0;      // cursor starts on "1"
      for (char ch : number.toCharArray()) {
        int d = ch - '0';
        int tx = d == 0 ? 1 : (d - 1) % 3, ty = d == 0 ? 3 : (d - 1) / 3;
        while (y < ty) { press(DOWN, 140); y++; }
        while (y > ty) { press(UP, 140); y--; }
        while (x < tx) { press(RIGHT, 140); x++; }
        while (x > tx) { press(LEFT, 140); x--; }
        press(A, 180);
      }
      press(A, 2600);        // call
      press(B, 400);
      press(B, 400);
      press(B, 400);         // phone away
    } finally {
      if (remove != null) { remove.setAccessible(true); remove.invoke(null, PAD_ID); }
    }
  }

  private void press(int key, long waitMs) throws Exception {
    padDown.invoke(null, PAD_ID, key, 0);
    Thread.sleep(70);
    padUp.invoke(null, PAD_ID, key, 0);
    Thread.sleep(waitMs);
  }

  // ------------------------------------------------------------ lifecycle
  @Override public void onActivityResumed(Activity a) { if (isGame(a)) attach(a); }
  @Override public void onActivityDestroyed(Activity a) { if (a == game) { detach(); game = null; } }
  @Override public void onActivityCreated(Activity a, Bundle b) { }
  @Override public void onActivityStarted(Activity a) { }
  @Override public void onActivityPaused(Activity a) { if (a == game) closeMenu(); }
  @Override public void onActivityStopped(Activity a) { }
  @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
}
