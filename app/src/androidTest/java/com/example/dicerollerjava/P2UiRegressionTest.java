package com.example.dicerollerjava;

import android.Manifest;
import android.app.UiAutomation;
import android.content.ContentResolver;
import android.content.Context;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityManager;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withTagValue;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** UI regressions for the Java/Kotlin comparison's P2 findings. */
public class P2UiRegressionTest extends BaseUiTest {
    private static final String READING_TIMEOUT_SETTING = "accessibility_non_interactive_ui_timeout_ms";
    private static final int DEFAULT_STATUS_TIMEOUT_MILLIS = 5_000;
    private static final int TEST_READING_TIMEOUT_MILLIS = 7_000;

    @Test public void currentModeRemainsVisibleWhenBrowsingOldHistory() {
        for (int index = 0; index < 8; index++) roll("d1", "1", RollMode.NORMAL);
        show("history_" + state().history.get(6).id);

        for (RollMode mode : RollMode.values()) {
            withModel(model -> model.setMode(mode));
            onView(withTagValue(is("current_mode"))).check(matches(isDisplayed()));
            withActivity(activity -> {
                TextView label = (TextView) find(activity, "current_mode");
                int expected = mode == RollMode.NORMAL ? R.string.mode_normal
                        : mode == RollMode.ADVANTAGE ? R.string.mode_advantage : R.string.mode_disadvantage;
                assertTrue("The fixed footer must describe the mode of the next roll",
                        label.getText().toString().contains(activity.getString(expected)));
                // A duplicate label inside the scrolling form would pass a text-only check.
                for (ViewParent parent = label.getParent(); parent != null; parent = parent.getParent()) {
                    assertFalse("The current mode must remain outside the scrolling content", parent instanceof ScrollView);
                }
                assertTrue("The test must actually be browsing older history",
                        ((ScrollView) find(activity, "main_list")).getScrollY() > 0);
            });
        }
    }

    @Test public void transientStatusUsesTheSystemReadingTimeoutAndStillExpires() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ContentResolver resolver = context.getContentResolver();
        AccessibilityManager accessibility = context.getSystemService(AccessibilityManager.class);
        assertNotNull(accessibility);
        UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        automation.adoptShellPermissionIdentity(Manifest.permission.WRITE_SECURE_SETTINGS);
        String previous = null;
        boolean previousRead = false;
        try {
            previous = Settings.Secure.getString(resolver, READING_TIMEOUT_SETTING);
            previousRead = true;
            assertTrue(Settings.Secure.putInt(resolver, READING_TIMEOUT_SETTING, TEST_READING_TIMEOUT_MILLIS));
            waitUntil(() -> accessibility.getRecommendedTimeoutMillis(DEFAULT_STATUS_TIMEOUT_MILLIS,
                    AccessibilityManager.FLAG_CONTENT_TEXT) == TEST_READING_TIMEOUT_MILLIS,
                    "The system did not apply the test reading timeout");

            roll("d1", "1", RollMode.NORMAL);
            onView(withTagValue(is("status"))).check(matches(isDisplayed()));
            withActivity(activity -> assertEquals(activity.getResources().getQuantityString(
                    R.plurals.rolls_completed, 1, 1), ((TextView) find(activity, "status")).getText().toString()));

            // The previous implementation always hid this real completion message at 5 s.
            // Test the visible outcome, without calling a private method or replacing the service.
            SystemClock.sleep(5_500);
            onView(withTagValue(is("status"))).check(matches(isDisplayed()));
            waitUntil(() -> {
                AtomicReference<Boolean> hidden = new AtomicReference<>(false);
                withActivity(activity -> hidden.set(find(activity, "status").getVisibility() == View.GONE));
                return hidden.get();
            }, "The temporary status message never expired after the recommended timeout");
        } finally {
            try {
                if (previousRead) assertTrue(Settings.Secure.putString(resolver, READING_TIMEOUT_SETTING, previous));
            } finally {
                automation.dropShellPermissionIdentity();
            }
        }
    }
}
