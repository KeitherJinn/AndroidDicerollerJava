package com.example.dicerollerjava;

import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Locale;

import static org.junit.Assert.*;

public class LocalizationTest {
    private Context localized(Locale locale) {
        Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.setLocales(new LocaleList(locale));
        return base.createConfigurationContext(configuration);
    }

    @Test public void everyApplicationStringLoadsInEnglishAndChinese() throws Exception {
        for (Locale locale : new Locale[] {Locale.ENGLISH, Locale.SIMPLIFIED_CHINESE}) {
            Context context = localized(locale);
            for (Field field : R.string.class.getFields()) {
                assertFalse(locale + " " + field.getName(), context.getString(field.getInt(null)).isEmpty());
            }
        }
        assertNotEquals(localized(Locale.ENGLISH).getString(R.string.dice_notation),
                localized(Locale.SIMPLIFIED_CHINESE).getString(R.string.dice_notation));
        assertNotEquals(localized(Locale.ENGLISH).getString(R.string.mode_advantage_hint),
                localized(Locale.SIMPLIFIED_CHINESE).getString(R.string.mode_advantage_hint));
    }

    @Test public void completedRollQuantitiesHaveFormattedLocalizedText() {
        for (Locale locale : new Locale[] {Locale.ENGLISH, Locale.SIMPLIFIED_CHINESE}) {
            Context context = localized(locale);
            for (int count : new int[] {1, 3, 100}) {
                String text = context.getResources().getQuantityString(R.plurals.rolls_completed, count, count);
                assertTrue(text.contains(Integer.toString(count)));
                assertFalse(text.contains("%d"));
            }
        }
    }
}
