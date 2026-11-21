package com.wastelanddroid.controls;

import android.content.Context;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class GamepadMapping {
    public static final Map<Integer, String> BUTTON_NAMES;
    public static final int PAD_DOWN = 10004;
    public static final int PAD_LEFT = 10005;
    public static final int PAD_LT = 10002;
    public static final int PAD_RIGHT = 10006;
    public static final int PAD_RT = 10001;
    public static final int PAD_UP = 10003;
    public final Map<Integer, Integer> keys = new LinkedHashMap();
    public final Set<Integer> toggles = new HashSet();
    public float lookSpeed = 1.0f;
    public float deadzone = 0.18f;
    public boolean invertY = false;

    static {
        LinkedHashMap linkedHashMap = new LinkedHashMap();
        BUTTON_NAMES = linkedHashMap;
        linkedHashMap.put(96, "A");
        linkedHashMap.put(97, "B");
        linkedHashMap.put(99, "X");
        linkedHashMap.put(100, "Y");
        linkedHashMap.put(102, "LB");
        linkedHashMap.put(103, "RB");
        linkedHashMap.put(Integer.valueOf(PAD_LT), "LT");
        linkedHashMap.put(Integer.valueOf(PAD_RT), "RT");
        linkedHashMap.put(106, "L3");
        linkedHashMap.put(107, "R3");
        linkedHashMap.put(108, "Start");
        linkedHashMap.put(109, "Back");
        linkedHashMap.put(Integer.valueOf(PAD_UP), "Крестовина вверх");
        linkedHashMap.put(Integer.valueOf(PAD_DOWN), "Крестовина вниз");
        linkedHashMap.put(Integer.valueOf(PAD_LEFT), "Крестовина влево");
        linkedHashMap.put(Integer.valueOf(PAD_RIGHT), "Крестовина вправо");
    }

    public static GamepadMapping defaults() {
        GamepadMapping gamepadMapping = new GamepadMapping();
        gamepadMapping.keys.put(96, 69);
        gamepadMapping.keys.put(97, 9);
        gamepadMapping.keys.put(99, 82);
        gamepadMapping.keys.put(100, 32);
        gamepadMapping.keys.put(102, 86);
        gamepadMapping.keys.put(103, 90);
        gamepadMapping.keys.put(Integer.valueOf(PAD_LT), 2);
        gamepadMapping.keys.put(Integer.valueOf(PAD_RT), 1);
        gamepadMapping.keys.put(106, 17);
        gamepadMapping.keys.put(107, 70);
        gamepadMapping.keys.put(108, 27);
        gamepadMapping.keys.put(109, 84);
        gamepadMapping.keys.put(Integer.valueOf(PAD_UP), 49);
        gamepadMapping.keys.put(Integer.valueOf(PAD_DOWN), 50);
        gamepadMapping.keys.put(Integer.valueOf(PAD_LEFT), 51);
        gamepadMapping.keys.put(Integer.valueOf(PAD_RIGHT), 52);
        gamepadMapping.toggles.add(106);
        return gamepadMapping;
    }

    private static File file(Context context) {
        return new File(context.getFilesDir(), "gamepad.json");
    }

    public static GamepadMapping load(Context context) throws JSONException {
        GamepadMapping gamepadMappingDefaults = defaults();
        try {
            File file = file(context);
            if (!file.exists()) {
                return gamepadMappingDefaults;
            }
            JSONObject jSONObject = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            JSONObject jSONObject2 = jSONObject.getJSONObject("keys");
            gamepadMappingDefaults.keys.clear();
            Iterator<String> itKeys = jSONObject2.keys();
            while (itKeys.hasNext()) {
                String next = itKeys.next();
                gamepadMappingDefaults.keys.put(Integer.valueOf(Integer.parseInt(next)), Integer.valueOf(jSONObject2.getInt(next)));
            }
            gamepadMappingDefaults.toggles.clear();
            JSONArray jSONArrayOptJSONArray = jSONObject.optJSONArray("toggles");
            if (jSONArrayOptJSONArray != null) {
                for (int i = 0; i < jSONArrayOptJSONArray.length(); i++) {
                    gamepadMappingDefaults.toggles.add(Integer.valueOf(jSONArrayOptJSONArray.getInt(i)));
                }
            }
            gamepadMappingDefaults.lookSpeed = (float) jSONObject.optDouble("lookSpeed", 1.0d);
            gamepadMappingDefaults.deadzone = (float) jSONObject.optDouble("deadzone", 0.18d);
            gamepadMappingDefaults.invertY = jSONObject.optBoolean("invertY", false);
            return gamepadMappingDefaults;
        } catch (Exception unused) {
            return defaults();
        }
    }

    public void save(Context context) throws Exception {
        JSONObject jSONObject = new JSONObject();
        for (Map.Entry<Integer, Integer> entry : this.keys.entrySet()) {
            jSONObject.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        JSONArray jSONArray = new JSONArray();
        Iterator<Integer> it = this.toggles.iterator();
        while (it.hasNext()) {
            jSONArray.put(it.next().intValue());
        }
        Files.write(file(context).toPath(), new JSONObject().put("keys", jSONObject).put("toggles", jSONArray).put("lookSpeed", this.lookSpeed).put("deadzone", this.deadzone).put("invertY", this.invertY).toString(2).getBytes(StandardCharsets.UTF_8), new OpenOption[0]);
    }
}
