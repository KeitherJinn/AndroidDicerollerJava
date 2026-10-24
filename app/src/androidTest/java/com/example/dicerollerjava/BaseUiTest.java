package com.example.dicerollerjava;

import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.widget.ScrollView;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.espresso.Espresso;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withTagValue;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/** Tests may mutate only the separate QA installation, never the deliverable application's data. */
public abstract class BaseUiTest {
    @Rule public final ActivityScenarioRule<MainActivity> activityRule = new ActivityScenarioRule<>(MainActivity.class);

    @Before public void resetIsolatedQaHistory() {
        withActivity(activity -> assertEquals("Refusing to modify a non-test installation",
                "com.example.dicerollerjava.uitest", activity.getPackageName()));
        settled();
        withModel(model -> {
            if (model.getState().blockedForRecovery) model.resetHistory();
        });
        settled();
        withModel(DiceRollerViewModel::clearHistory);
        settled();
        withModel(model -> {
            model.setName("");
            model.setNotation("d20");
            model.setTimes("1");
            model.setMode(RollMode.NORMAL);
        });
    }

    protected void withActivity(Consumer<MainActivity> action) {
        activityRule.getScenario().onActivity(action::accept);
    }

    protected void withModel(Consumer<DiceRollerViewModel> action) {
        withActivity(activity -> action.accept(activity.getModel()));
    }

    protected RollerUiState state() {
        AtomicReference<RollerUiState> value = new AtomicReference<>();
        withModel(model -> value.set(model.getState()));
        return value.get();
    }

    protected HistoryLoadResult diskHistory() {
        AtomicReference<File> directory = new AtomicReference<>();
        withActivity(activity -> directory.set(activity.getFilesDir()));
        try {
            return new HistoryStore(directory.get()).load();
        } catch (IOException exception) {
            throw new AssertionError("Could not inspect persisted QA history", exception);
        }
    }

    protected void settled() {
        waitUntil(() -> !state().busy, "Background operation did not finish");
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    protected void waitUntil(BooleanSupplier predicate, String failure) {
        long deadline = SystemClock.uptimeMillis() + 15_000;
        while (!predicate.getAsBoolean()) {
            if (SystemClock.uptimeMillis() >= deadline) fail(failure);
            SystemClock.sleep(40);
        }
    }

    protected View find(MainActivity activity, String tag) {
        View view = activity.getWindow().getDecorView().findViewWithTag(tag);
        assertNotNull("Missing view tag: " + tag, view);
        return view;
    }

    protected void show(String tag) {
        waitUntil(() -> {
            AtomicReference<Boolean> ready = new AtomicReference<>(false);
            withActivity(activity -> {
                View view = find(activity, tag);
                ready.set(view.isLaidOut() && !view.isLayoutRequested()
                        && !activity.getWindow().getDecorView().isLayoutRequested());
            });
            return ready.get();
        }, "View did not finish layout: " + tag);
        withActivity(activity -> {
            View view = find(activity, tag);
            view.requestRectangleOnScreen(new Rect(0, 0, view.getWidth(), view.getHeight()), true);
            // Rotation tests deliberately select the leading visible history card. A mere
            // requestRectangleOnScreen can leave it at the bottom of a tall portrait viewport,
            // outside the smaller landscape viewport even when the app preserves its leading anchor correctly.
            if (tag.startsWith("history_")) {
                ScrollView scroll = (ScrollView) find(activity, "main_list");
                int[] location = new int[2], origin = new int[2];
                view.getLocationInWindow(location); scroll.getLocationInWindow(origin);
                scroll.scrollTo(0, scroll.getScrollY() + location[1] - origin[1]);
            }
        });
        AtomicReference<String> previous = new AtomicReference<>();
        AtomicLong stableSince = new AtomicLong(SystemClock.uptimeMillis());
        // Espresso waits for the message queue, but ScrollView/IME animations can keep
        // moving a target after the queue is idle. Verify stable visible geometry before injecting a real tap.
        waitUntil(() -> {
            AtomicReference<String> geometry = new AtomicReference<>();
            withActivity(activity -> {
                View view = find(activity, tag);
                Rect visible = new Rect();
                int[] location = new int[2];
                view.getLocationOnScreen(location);
                if (view.isLaidOut() && !view.isLayoutRequested() && view.getGlobalVisibleRect(visible)
                        && !activity.getWindow().getDecorView().isLayoutRequested()) {
                    geometry.set(location[0] + ":" + location[1] + ":" + view.getWidth() + ":"
                            + view.getHeight() + ":" + visible.toShortString());
                }
            });
            String current = geometry.get();
            if (current == null || !current.equals(previous.get())) {
                previous.set(current);
                stableSince.set(SystemClock.uptimeMillis());
                return false;
            }
            return SystemClock.uptimeMillis() - stableSince.get() >= 180;
        }, "View did not become visible and stationary: " + tag);
    }

    protected void clickTag(String tag) {
        if (!tag.equals("notation") && !tag.equals("times") && !tag.equals("name")
                && !tag.equals("roll_button") && !tag.equals("cancel_roll")) Espresso.closeSoftKeyboard();
        show(tag);
        onView(withTagValue(is(tag))).perform(click());
    }

    protected void clickDialogTag(String tag) {
        onView(withTagValue(is(tag))).perform(click());
    }

    protected void roll(String expression, String times, RollMode mode) {
        withModel(model -> {
            model.setNotation(expression);
            model.setTimes(times);
            model.setMode(mode);
            org.junit.Assert.assertTrue(model.roll());
        });
        settled();
    }
}
