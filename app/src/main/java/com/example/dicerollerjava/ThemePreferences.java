package com.example.dicerollerjava;

import android.content.Context;
import android.content.SharedPreferences;

/** Appearance is persisted separately from roll history. */
public final class ThemePreferences {
    public static final String FILE_NAME = "appearance";
    public static final String KEY_PALETTE = "palette";
    private final SharedPreferences preferences;

    public ThemePreferences(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    public ThemePalette readPalette() {
        try {
            return ThemePalette.fromStorageKey(preferences.getString(KEY_PALETTE, null));
        } catch (ClassCastException invalidStoredType) {
            return ThemePalette.SYSTEM;
        }
    }

    public void setPalette(ThemePalette palette) {
        if (palette == null) throw new IllegalArgumentException("palette must not be null");
        preferences.edit().putString(KEY_PALETTE, palette.storageKey).apply();
    }
}
