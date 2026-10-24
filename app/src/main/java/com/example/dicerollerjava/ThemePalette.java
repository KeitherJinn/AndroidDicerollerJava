package com.example.dicerollerjava;

import android.content.Context;

/** Stable preference keys do not depend on display language or enum order. */
public enum ThemePalette {
    SYSTEM("system"), VIOLET("violet"), OCEAN("ocean"), FOREST("forest"), AMBER("amber");

    public final String storageKey;

    ThemePalette(String storageKey) { this.storageKey = storageKey; }

    public static ThemePalette fromStorageKey(String key) {
        for (ThemePalette palette : values()) {
            if (palette.storageKey.equals(key)) return palette;
        }
        return SYSTEM;
    }

    /** Static colors permit plain JVM tests; SYSTEM falls back to Violet in this method. */
    public ThemeColors fixedColors(boolean dark) {
        switch (this) {
            case OCEAN: return ThemeColors.ocean(dark);
            case FOREST: return ThemeColors.forest(dark);
            case AMBER: return ThemeColors.amber(dark);
            default: return ThemeColors.violet(dark);
        }
    }

    public ThemeColors resolve(Context context, boolean dark) {
        if (this != SYSTEM) return fixedColors(dark);
        // All supported devices are API 34+, which exposes these semantic dynamic-color roles.
        int[] primary = colors(context, dark
                ? new int[] {android.R.color.system_primary_dark, android.R.color.system_on_primary_dark,
                android.R.color.system_primary_container_dark, android.R.color.system_on_primary_container_dark}
                : new int[] {android.R.color.system_primary_light, android.R.color.system_on_primary_light,
                android.R.color.system_primary_container_light, android.R.color.system_on_primary_container_light});
        int[] secondary = colors(context, dark
                ? new int[] {android.R.color.system_secondary_dark, android.R.color.system_on_secondary_dark,
                android.R.color.system_secondary_container_dark, android.R.color.system_on_secondary_container_dark}
                : new int[] {android.R.color.system_secondary_light, android.R.color.system_on_secondary_light,
                android.R.color.system_secondary_container_light, android.R.color.system_on_secondary_container_light});
        int[] tertiary = colors(context, dark
                ? new int[] {android.R.color.system_tertiary_dark, android.R.color.system_on_tertiary_dark,
                android.R.color.system_tertiary_container_dark, android.R.color.system_on_tertiary_container_dark}
                : new int[] {android.R.color.system_tertiary_light, android.R.color.system_on_tertiary_light,
                android.R.color.system_tertiary_container_light, android.R.color.system_on_tertiary_container_light});
        int[] surfaces = colors(context, dark
                ? new int[] {android.R.color.system_surface_dark, android.R.color.system_on_surface_dark,
                android.R.color.system_surface_variant_dark, android.R.color.system_on_surface_variant_dark,
                android.R.color.system_outline_dark, android.R.color.system_outline_variant_dark,
                android.R.color.system_neutral1_100, android.R.color.system_neutral1_800,
                android.R.color.system_surface_dim_dark, android.R.color.system_surface_bright_dark,
                android.R.color.system_surface_container_lowest_dark, android.R.color.system_surface_container_low_dark,
                android.R.color.system_surface_container_dark, android.R.color.system_surface_container_high_dark,
                android.R.color.system_surface_container_highest_dark}
                : new int[] {android.R.color.system_surface_light, android.R.color.system_on_surface_light,
                android.R.color.system_surface_variant_light, android.R.color.system_on_surface_variant_light,
                android.R.color.system_outline_light, android.R.color.system_outline_variant_light,
                android.R.color.system_neutral1_800, android.R.color.system_neutral1_50,
                android.R.color.system_surface_dim_light, android.R.color.system_surface_bright_light,
                android.R.color.system_surface_container_lowest_light, android.R.color.system_surface_container_low_light,
                android.R.color.system_surface_container_light, android.R.color.system_surface_container_high_light,
                android.R.color.system_surface_container_highest_light});
        int[] errors = colors(context, dark
                ? new int[] {android.R.color.system_error_dark, android.R.color.system_on_error_dark,
                android.R.color.system_error_container_dark, android.R.color.system_on_error_container_dark}
                : new int[] {android.R.color.system_error_light, android.R.color.system_on_error_light,
                android.R.color.system_error_container_light, android.R.color.system_on_error_container_light});
        return new ThemeColors(primary, secondary, tertiary, surfaces, errors);
    }

    private static int[] colors(Context context, int[] ids) {
        int[] resolved = new int[ids.length];
        for (int index = 0; index < ids.length; index++) resolved[index] = context.getColor(ids[index]);
        return resolved;
    }
}
