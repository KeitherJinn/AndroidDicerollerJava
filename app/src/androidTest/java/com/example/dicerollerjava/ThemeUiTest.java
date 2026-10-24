package com.example.dicerollerjava;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ThemeUiTest extends BaseUiTest {
    private Object previousPreference;
    private boolean capturedPreference;

    @Before public void resetPalette() {
        withActivity(activity -> {
            SharedPreferences preferences = preferences(activity);
            previousPreference = preferences.getAll().get(ThemePreferences.KEY_PALETTE);
            capturedPreference = true;
            assertTrue(preferences.edit().putString(ThemePreferences.KEY_PALETTE, "system").commit());
        });
        activityRule.getScenario().recreate();
        settled();
    }

    @After public void restoreQaPalette() {
        if (!capturedPreference) return;
        withActivity(activity -> {
            SharedPreferences.Editor editor = preferences(activity).edit();
            if (previousPreference instanceof String) editor.putString(ThemePreferences.KEY_PALETTE, (String) previousPreference);
            else if (previousPreference instanceof Integer) editor.putInt(ThemePreferences.KEY_PALETTE, (Integer) previousPreference);
            else editor.remove(ThemePreferences.KEY_PALETTE);
            assertTrue(editor.commit());
        });
    }

    private SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(ThemePreferences.FILE_NAME, Context.MODE_PRIVATE);
    }

    private void selectPalette(ThemePalette palette) {
        clickTag("palette_button");
        clickDialogTag("palette_option_" + palette.name());
        clickDialogTag("palette_done");
        settled();
    }

    private int expectedPrimary(ThemePalette palette) {
        AtomicInteger expected = new AtomicInteger();
        withActivity(activity -> {
            boolean dark = (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES;
            expected.set(palette.resolve(activity, dark).primary);
        });
        return expected.get();
    }

    private int renderedPrimary() {
        show("roll_button");
        AtomicInteger rendered = new AtomicInteger();
        withActivity(activity -> {
            View button = find(activity, "roll_button");
            Bitmap bitmap = Bitmap.createBitmap(button.getWidth(), button.getHeight(), Bitmap.Config.ARGB_8888);
            button.draw(new Canvas(bitmap));
            rendered.set(bitmap.getPixel(bitmap.getWidth() / 8, bitmap.getHeight() / 2));
            bitmap.recycle();
        });
        return rendered.get();
    }

    @Test public void everyPaletteChangesRenderedButtonWithoutChangingInputsOrHistory() {
        withModel(model -> { model.setName("Attack"); model.setNotation("2d6+3"); model.setTimes("3"); model.setMode(RollMode.ADVANTAGE); });
        List<RollBatch> history = state().history;
        Set<Integer> rendered = new HashSet<>();
        for (ThemePalette palette : ThemePalette.values()) {
            selectPalette(palette);
            withActivity(activity -> assertEquals(palette, new ThemePreferences(activity).readPalette()));
            assertEquals("Attack", state().name);
            assertEquals("2d6+3", state().notation);
            assertEquals("3", state().timesText);
            assertEquals(RollMode.ADVANTAGE, state().mode);
            assertEquals(history, state().history);
            int actual = renderedPrimary();
            assertEquals("Rendered primary for " + palette, expectedPrimary(palette), actual);
            rendered.add(actual);
        }
        assertTrue("Four fixed palettes must visibly differ", rendered.size() >= 4);
    }

    @Test public void paletteSurvivesRecreationAndFreshPreferenceReader() {
        selectPalette(ThemePalette.OCEAN);
        int before = renderedPrimary();
        activityRule.getScenario().recreate();
        settled();
        withActivity(activity -> assertEquals(ThemePalette.OCEAN,
                new ThemePreferences(activity.getApplicationContext()).readPalette()));
        assertEquals(before, renderedPrimary());
    }

    @Test public void invalidStoredKeyAndWrongTypeFallBackToSystem() {
        withActivity(activity -> assertTrue(preferences(activity).edit().putString(ThemePreferences.KEY_PALETTE, "future-palette").commit()));
        activityRule.getScenario().recreate();
        settled();
        withActivity(activity -> assertEquals(ThemePalette.SYSTEM, new ThemePreferences(activity).readPalette()));
        assertEquals(expectedPrimary(ThemePalette.SYSTEM), renderedPrimary());
        withActivity(activity -> assertTrue(preferences(activity).edit().putInt(ThemePreferences.KEY_PALETTE, 42).commit()));
        activityRule.getScenario().recreate();
        settled();
        withActivity(activity -> assertEquals(ThemePalette.SYSTEM, new ThemePreferences(activity).readPalette()));
        assertEquals(expectedPrimary(ThemePalette.SYSTEM), renderedPrimary());
    }

    @Test public void clearingHistoryPreservesPalette() {
        selectPalette(ThemePalette.FOREST);
        roll("d1", "1", RollMode.NORMAL);
        clickTag("clear_history");
        clickDialogTag("confirm_clear");
        settled();
        assertTrue(state().history.isEmpty());
        withActivity(activity -> assertEquals(ThemePalette.FOREST, new ThemePreferences(activity).readPalette()));
        assertEquals(expectedPrimary(ThemePalette.FOREST), renderedPrimary());
    }

    @Test public void platformDynamicColorsResolveBothLightAndDarkSchemes() {
        withActivity(activity -> {
            ThemeColors light = ThemePalette.SYSTEM.resolve(activity, false);
            ThemeColors dark = ThemePalette.SYSTEM.resolve(activity, true);
            assertEquals(activity.getColor(android.R.color.system_primary_light), light.primary);
            assertEquals(activity.getColor(android.R.color.system_primary_dark), dark.primary);
            assertEquals(activity.getColor(android.R.color.system_surface_light), light.surface);
            assertEquals(activity.getColor(android.R.color.system_surface_dark), dark.surface);
            assertNotEquals(light.surface, dark.surface);
        });
    }
}
