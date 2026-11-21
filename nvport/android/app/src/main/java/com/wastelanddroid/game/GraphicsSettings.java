package com.wastelanddroid.game;

import android.content.Context;
import android.content.SharedPreferences;
import com.wastelanddroid.game.DeviceProfile;
import com.wastelanddroid.util.IniFile;
import com.wastelanddroid.util.Prefs;
import java.util.Locale;

public final class GraphicsSettings {
    private static final String P = "gfx_";
    public int anisotropy;
    public boolean bloom;
    public boolean decals;
    public int fade;
    public int fov;
    public int grassDensity;
    public boolean hdr;
    public int shadows;
    public int textureQuality;
    public boolean treeShadows;
    public int viewDistance;
    public boolean waterReflections;

    public static GraphicsSettings preset(DeviceProfile.Quality quality) {
        GraphicsSettings graphicsSettings = new GraphicsSettings();
        graphicsSettings.fov = 75;
        int i = AnonymousClass1.$SwitchMap$com$wastelanddroid$game$DeviceProfile$Quality[quality.ordinal()];
        if (i == 1) {
            graphicsSettings.textureQuality = 0;
            graphicsSettings.anisotropy = 8;
            graphicsSettings.shadows = 2;
            graphicsSettings.viewDistance = 8;
            graphicsSettings.grassDensity = 3;
            graphicsSettings.waterReflections = true;
            graphicsSettings.hdr = true;
            graphicsSettings.bloom = false;
            graphicsSettings.decals = true;
            graphicsSettings.treeShadows = false;
            graphicsSettings.fade = 8;
        } else if (i == 2) {
            graphicsSettings.textureQuality = 0;
            graphicsSettings.anisotropy = 4;
            graphicsSettings.shadows = 1;
            graphicsSettings.viewDistance = 6;
            graphicsSettings.grassDensity = 2;
            graphicsSettings.waterReflections = true;
            graphicsSettings.hdr = true;
            graphicsSettings.bloom = false;
            graphicsSettings.decals = true;
            graphicsSettings.treeShadows = false;
            graphicsSettings.fade = 6;
        } else {
            graphicsSettings.textureQuality = 1;
            graphicsSettings.anisotropy = 4;
            graphicsSettings.shadows = 0;
            graphicsSettings.viewDistance = 4;
            graphicsSettings.grassDensity = 1;
            graphicsSettings.waterReflections = false;
            graphicsSettings.hdr = false;
            graphicsSettings.bloom = true;
            graphicsSettings.decals = true;
            graphicsSettings.treeShadows = false;
            graphicsSettings.fade = 4;
        }
        return graphicsSettings;
    }

    /* renamed from: com.wastelanddroid.game.GraphicsSettings$1, reason: invalid class name */
    static /* synthetic */ class AnonymousClass1 {
        static final /* synthetic */ int[] $SwitchMap$com$wastelanddroid$game$DeviceProfile$Quality;

        static {
            int[] iArr = new int[DeviceProfile.Quality.values().length];
            $SwitchMap$com$wastelanddroid$game$DeviceProfile$Quality = iArr;
            try {
                iArr[DeviceProfile.Quality.HIGH.ordinal()] = 1;
            } catch (NoSuchFieldError unused) {
            }
            try {
                $SwitchMap$com$wastelanddroid$game$DeviceProfile$Quality[DeviceProfile.Quality.MEDIUM.ordinal()] = 2;
            } catch (NoSuchFieldError unused2) {
            }
        }
    }

    public static GraphicsSettings load(Context context, DeviceProfile.Quality quality) {
        SharedPreferences sharedPreferences = Prefs.get(context);
        GraphicsSettings graphicsSettingsPreset = preset(quality);
        if (!sharedPreferences.getBoolean("gfx_custom", false)) {
            return graphicsSettingsPreset;
        }
        graphicsSettingsPreset.textureQuality = sharedPreferences.getInt("gfx_tex", graphicsSettingsPreset.textureQuality);
        graphicsSettingsPreset.anisotropy = sharedPreferences.getInt("gfx_aniso", graphicsSettingsPreset.anisotropy);
        graphicsSettingsPreset.shadows = sharedPreferences.getInt("gfx_shadows", graphicsSettingsPreset.shadows);
        graphicsSettingsPreset.viewDistance = sharedPreferences.getInt("gfx_view", graphicsSettingsPreset.viewDistance);
        graphicsSettingsPreset.grassDensity = sharedPreferences.getInt("gfx_grass", graphicsSettingsPreset.grassDensity);
        graphicsSettingsPreset.waterReflections = sharedPreferences.getBoolean("gfx_water", graphicsSettingsPreset.waterReflections);
        graphicsSettingsPreset.hdr = sharedPreferences.getBoolean("gfx_hdr", graphicsSettingsPreset.hdr);
        graphicsSettingsPreset.bloom = sharedPreferences.getBoolean("gfx_bloom", graphicsSettingsPreset.bloom);
        graphicsSettingsPreset.decals = sharedPreferences.getBoolean("gfx_decals", graphicsSettingsPreset.decals);
        graphicsSettingsPreset.treeShadows = sharedPreferences.getBoolean("gfx_treeshadows", graphicsSettingsPreset.treeShadows);
        graphicsSettingsPreset.fov = sharedPreferences.getInt("gfx_fov", graphicsSettingsPreset.fov);
        graphicsSettingsPreset.fade = sharedPreferences.getInt("gfx_fade", graphicsSettingsPreset.fade);
        return graphicsSettingsPreset;
    }

    public void save(Context context) {
        Prefs.get(context).edit().putBoolean("gfx_custom", true).putInt("gfx_tex", this.textureQuality).putInt("gfx_aniso", this.anisotropy).putInt("gfx_shadows", this.shadows).putInt("gfx_view", this.viewDistance).putInt("gfx_grass", this.grassDensity).putBoolean("gfx_water", this.waterReflections).putBoolean("gfx_hdr", this.hdr).putBoolean("gfx_bloom", this.bloom).putBoolean("gfx_decals", this.decals).putBoolean("gfx_treeshadows", this.treeShadows).putInt("gfx_fov", this.fov).putInt("gfx_fade", this.fade).apply();
    }

    public static void resetToPreset(Context context) {
        Prefs.get(context).edit().putBoolean("gfx_custom", false).apply();
    }

    private static String f(float f) {
        return String.format(Locale.US, "%.4f", Float.valueOf(f));
    }

    public void apply(IniFile iniFile, IniFile iniFile2) {
        iniFile2.set("Display", "iTexMipMapSkip", String.valueOf(this.textureQuality));
        iniFile2.set("Display", "iMaxAnisotropy", String.valueOf(this.anisotropy));
        iniFile2.set("Display", "fDefaultFOV", f(this.fov));
        iniFile2.set("Display", "fDefaultWorldFOV", f(this.fov));
        iniFile2.set("Display", "fDefault1stPersonFOV", f(Math.min(this.fov, 85)));
        boolean z = this.shadows > 0;
        iniFile2.set("Display", "bDrawShadows", z ? "1" : "0");
        iniFile2.set("Display", "iActorShadowCountExt", String.valueOf(z ? this.shadows * 2 : 0));
        iniFile2.set("Display", "iActorShadowCountInt", String.valueOf(z ? this.shadows * 3 : 0));
        iniFile2.set("Display", "iShadowMapResolution", this.shadows >= 3 ? "1024" : "512");
        iniFile2.set("Display", "iShadowFilter", z ? String.valueOf(Math.min(this.shadows, 2)) : "0");
        iniFile2.set("Display", "bDoCanopyShadowPass", this.treeShadows ? "1" : "0");
        iniFile2.set("Display", "bShadowsOnGrass", "0");
        iniFile2.set("BlurShaderHDR", "bDoHighDynamicRange", this.hdr ? "1" : "0");
        iniFile2.set("BlurShader", "bUseBlurShader", (this.hdr || !this.bloom) ? "0" : "1");
        iniFile2.set("Display", "bDecalsOnSkinnedGeometry", this.decals ? "1" : "0");
        iniFile2.set("Display", "iMaxDecalsPerFrame", this.decals ? "20" : "0");
        iniFile2.set("Display", "iMaxSkinDecalsPerFrame", this.decals ? "10" : "0");
        iniFile2.set("Water", "bUseWaterReflections", this.waterReflections ? "1" : "0");
        iniFile2.set("Water", "bUseWaterReflectionsMisc", this.waterReflections ? "1" : "0");
        iniFile2.set("Water", "bUseWaterReflectionsStatics", this.waterReflections ? "1" : "0");
        iniFile2.set("Water", "bUseWaterReflectionsTrees", "0");
        iniFile2.set("Water", "bUseWaterReflectionsActors", "0");
        iniFile.set("Water", "bUseWaterReflections", this.waterReflections ? "1" : "0");
        iniFile2.set("LOD", "fLODFadeOutMultObjects", f((this.viewDistance * 1.6f) + 4.0f));
        iniFile2.set("LOD", "fLODFadeOutMultItems", f((this.fade * 0.6f) + 1.5f));
        iniFile2.set("LOD", "fLODFadeOutMultActors", f((this.fade * 1.0f) + 3.0f));
        iniFile2.set("Display", "fSpecularLODStartFade", f((this.fade * 150.0f) + 400.0f));
        iniFile2.set("Display", "fLightLODStartFade", f((this.fade * 150.0f) + 400.0f));
        iniFile2.set("TerrainManager", "fBlockLoadDistanceLow", f((this.viewDistance * 5000.0f) + 15000.0f));
        iniFile2.set("TerrainManager", "fBlockLoadDistance", f((this.viewDistance * 10000.0f) + 50000.0f));
        iniFile2.set("TerrainManager", "fTreeLoadDistance", f((this.viewDistance * 5000.0f) + 10000.0f));
        boolean z2 = this.grassDensity > 0;
        iniFile2.set("Grass", "bGrassPointLighting", "0");
        iniFile2.set("Grass", "fGrassStartFadeDistance", f(z2 ? (this.grassDensity * 1500.0f) + 1500.0f : 0.0f));
        iniFile2.set("Grass", "fGrassMaxStartFadeDistance", f(z2 ? 7000.0f : 0.0f));
        iniFile2.set("Grass", "iMinGrassSize", String.valueOf(z2 ? 100 - (this.grassDensity * 20) : 120));
    }
}
