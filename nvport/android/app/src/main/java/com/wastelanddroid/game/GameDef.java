package com.wastelanddroid.game;

import android.content.Context;
import android.content.SharedPreferences;
import com.wastelanddroid.util.Prefs;

public enum GameDef {
    FO3("fo3", "Fallout 3", "Fallout3.exe", "Fallout3", "fose_loader.exe", "FOSE", new String[]{"Data/Fallout3.esm", "Data/Fallout - Meshes.bsa", "Data/Fallout - Textures.bsa", "Data/Fallout - Sound.bsa", "Data/Fallout - Voices.bsa", "Data/Fallout - MenuVoices.bsa", "Data/Fallout - Misc.bsa"}, new String[]{"Anchorage", "ThePitt", "BrokenSteel", "PointLookout", "Zeta"}, -10518705, -15064554),
    FNV("fnv", "Fallout: New Vegas", "FalloutNV.exe", "FalloutNV", "nvse_loader.exe", "NVSE", new String[]{"Data/FalloutNV.esm", "Data/Fallout - Meshes.bsa", "Data/Fallout - Textures.bsa", "Data/Fallout - Textures2.bsa", "Data/Fallout - Sound.bsa", "Data/Fallout - Voices1.bsa", "Data/Fallout - Misc.bsa"}, new String[]{"DeadMoney", "HonestHearts", "OldWorldBlues", "LonesomeRoad", "GunRunnersArsenal", "CaravanPack", "ClassicPack", "MercenaryPack", "TribalPack"}, -4958934, -14020086);

    public final int accent;
    public final int dark;
    public final String[] dlcs;
    public final String exe;
    public final String folder;
    public final String id;
    public final String[] requiredFiles;
    public final String scriptExtender;
    public final String scriptExtenderName;
    public final String title;

    GameDef(String str, String str2, String str3, String str4, String str5, String str6, String[] strArr, String[] strArr2, int i, int i2) {
        this.id = str;
        this.title = str2;
        this.exe = str3;
        this.folder = str4;
        this.scriptExtender = str5;
        this.scriptExtenderName = str6;
        this.requiredFiles = strArr;
        this.dlcs = strArr2;
        this.accent = i;
        this.dark = i2;
    }

    public static GameDef byId(String str) {
        for (GameDef gameDef : values()) {
            if (gameDef.id.equals(str)) {
                return gameDef;
            }
        }
        return FO3;
    }

    public String dirKey() {
        return "game_dir_" + this.id;
    }

    public String gameDir(Context context) {
        SharedPreferences sharedPreferences = Prefs.get(context);
        String string = sharedPreferences.getString(dirKey(), null);
        return (string == null && this == FO3) ? sharedPreferences.getString(Prefs.GAME_DIR, null) : string;
    }
}
