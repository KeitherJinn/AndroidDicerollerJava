package com.example.dicerollerjava;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ThemeColorsTest {
    @Test public void fixedLightAndDarkPalettesKeepNormalTextReadable() {
        for (ThemePalette palette : ThemePalette.values()) {
            if (palette == ThemePalette.SYSTEM) continue;
            for (boolean dark : new boolean[] {false, true}) {
                ThemeColors c = palette.fixedColors(dark);
                int[][] pairs = {{c.onPrimary, c.primary}, {c.onSecondaryContainer, c.secondaryContainer},
                        {c.onSurface, c.surface}, {c.onSurfaceVariant, c.surfaceContainer},
                        {c.onErrorContainer, c.errorContainer}, {c.onSurfaceVariant, c.secondaryContainer},
                        {c.primary, c.secondaryContainer}, {c.primary, c.errorContainer},
                        {c.onPrimaryContainer, c.primaryContainer}, {c.onSecondary, c.secondary},
                        {c.onTertiary, c.tertiary}, {c.onTertiaryContainer, c.tertiaryContainer}};
                for (int[] pair : pairs) {
                    double contrast = contrast(pair[0], pair[1]);
                    assertTrue(palette + " dark=" + dark + " contrast=" + contrast, contrast >= 4.5);
                }
            }
        }
    }

    @Test public void keysRoundTripAndUnknownKeysFallBackToSystem() {
        for (ThemePalette palette : ThemePalette.values()) {
            assertEquals(palette, ThemePalette.fromStorageKey(palette.storageKey));
        }
        assertEquals(ThemePalette.SYSTEM, ThemePalette.fromStorageKey(null));
        assertEquals(ThemePalette.SYSTEM, ThemePalette.fromStorageKey("future-palette"));
    }

    private static double contrast(int foreground, int background) {
        double first = luminance(foreground);
        double second = luminance(background);
        return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
    }

    private static double luminance(int color) {
        return linear((color >> 16) & 255) * 0.2126 + linear((color >> 8) & 255) * 0.7152
                + linear(color & 255) * 0.0722;
    }

    private static double linear(int component) {
        double value = component / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}
