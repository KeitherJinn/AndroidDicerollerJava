package com.example.dicerollerjava;

import android.app.UiAutomation;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Configuration;
import android.provider.Settings;
import android.view.Surface;
import android.view.View;
import android.view.WindowInsets;

import androidx.test.espresso.Espresso;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.isFocused;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withTagValue;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.*;

public class RollerUiTest extends BaseUiTest {
    @Test public void optionalNameAndLongResultAreDisplayedAndPersisted() {
        show("notation");
        onView(withTagValue(is("notation"))).perform(replaceText("d1+2147483647"));
        clickTag("roll_button");
        settled();
        show("final_result");
        onView(withTagValue(is("final_result"))).check(matches(withText(NumberFormat.getIntegerInstance().format(2147483648L))));
        assertEquals(2147483648L, diskHistory().batches.get(0).results.get(0).finalResult);
        assertEquals("", state().name);
    }

    @Test public void recreationPreservesInputsModeAndHistory() {
        withModel(model -> model.setName("Attack"));
        roll("2d6+3", "3", RollMode.ADVANTAGE);
        List<RollBatch> history = state().history;
        activityRule.getScenario().recreate();
        settled();
        assertEquals("Attack", state().name);
        assertEquals("2d6+3", state().notation);
        assertEquals("3", state().timesText);
        assertEquals(RollMode.ADVANTAGE, state().mode);
        assertEquals(history, state().history);
    }

    @Test public void quickDiceOnlyReplaceExpression() {
        withModel(model -> {
            model.setName("Attack"); model.setTimes("3"); model.setMode(RollMode.ADVANTAGE);
        });
        for (int sides : new int[] {4, 6, 8, 10, 12, 20, 100}) {
            clickTag("quick_d" + sides);
            assertEquals(new DiceExpression(1, sides, 0), DiceEngine.parseDiceNotation(state().notation));
            assertEquals("Attack", state().name);
            assertEquals("3", state().timesText);
            assertEquals(RollMode.ADVANTAGE, state().mode);
            assertTrue(state().history.isEmpty());
        }
    }

    @Test public void modeLabelSelectsWholeGroupAdvantage() {
        clickTag("advanced_toggle");
        clickTag("mode_ADVANTAGE");
        assertEquals(RollMode.ADVANTAGE, state().mode);
        withActivity(activity -> assertTrue(((android.widget.RadioButton) find(activity, "mode_ADVANTAGE")).isChecked()));
        onView(withText(R.string.mode_advantage_hint)).check(matches(withText(R.string.mode_advantage_hint)));
    }

    @Test public void hugeRepeatIsRejectedAndErrorClearsOnEdit() {
        clickTag("advanced_toggle");
        show("times");
        onView(withTagValue(is("times"))).perform(replaceText("2147483647"));
        clickTag("roll_button");
        assertEquals(InputIssue.REPEAT_LIMIT, state().timesIssue);
        assertTrue(state().history.isEmpty());
        show("times");
        onView(withTagValue(is("times"))).perform(replaceText("1"));
        assertNull(state().timesIssue);
    }

    @Test public void oversizedPasteCannotRollTruncatedModifier() {
        show("notation");
        onView(withTagValue(is("notation"))).perform(replaceText("d1" + new String(new char[123]).replace('\0', ' ') + "+100"));
        clickTag("roll_button");
        settled();
        assertTrue(state().history.isEmpty());
        assertEquals(InputIssue.NOTATION_LENGTH, state().notationIssue);
        assertEquals("d20", state().notation);
        show("notation");
        onView(withTagValue(is("notation"))).perform(replaceText("d1+100"));
        clickTag("roll_button");
        settled();
        assertEquals(101L, state().history.get(0).total);
    }

    @Test public void summaryBatchReuseAndIndividualReuseKeepOriginalRecords() {
        withModel(model -> model.setName("Attack"));
        roll("d1+2", "3", RollMode.ADVANTAGE);
        RollBatch batch = state().history.get(0);
        assertEquals(3, batch.count);
        assertEquals(9L, batch.total);
        withModel(model -> { model.setName(""); model.setNotation("d20"); model.setTimes("1"); });
        clickTag("load_batch_" + batch.id);
        assertEquals("1d1+2", state().notation);
        assertEquals("Attack", state().name);
        assertEquals("3", state().timesText);
        assertEquals(RollMode.ADVANTAGE, state().mode);
        assertEquals(1, state().history.size());
        clickTag("latest_details");
        clickTag("load_result_" + batch.results.get(1).id);
        assertEquals("1", state().timesText);
        assertEquals("1d1+2", state().notation);
        assertEquals(1, state().history.size());
    }

    @Test public void copyingIncludesAllDiceAndDetailsCanBePaged() {
        roll("137d1+2", "1", RollMode.NORMAL);
        RollBatch batch = state().history.get(0);
        clickTag("copy_batch_" + batch.id);
        AtomicReference<String> copied = new AtomicReference<>();
        withActivity(activity -> {
            ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            assertNotNull(clipboard.getPrimaryClip());
            copied.set(clipboard.getPrimaryClip().getItemAt(0).coerceToText(activity).toString());
        });
        String text = copied.get();
        String[] dice = text.substring(text.indexOf('[') + 1, text.indexOf(']')).split(", ");
        assertEquals(137, dice.length);
        for (String die : dice) assertEquals("1", die);
        assertTrue(text.contains("137d1+2"));
        assertTrue(text.contains("139"));
        clickTag("latest_details");
        String group = "group_" + batch.results.get(0).id + "_first";
        clickTag(group + "_next");
        clickTag(group + "_next");
        onView(withTagValue(is(group + "_next"))).check(matches(not(isEnabled())));
    }

    @Test public void clearConfirmationCanBeCancelledAndClearsDiskOnlyWhenConfirmed() {
        roll("d1", "1", RollMode.NORMAL);
        clickTag("clear_history");
        onView(withId(android.R.id.button2)).perform(click());
        assertEquals(1, state().history.size());
        clickTag("clear_history");
        clickDialogTag("confirm_clear");
        settled();
        assertTrue(state().history.isEmpty());
        assertTrue(diskHistory().batches.isEmpty());
    }

    @Test public void cancellationNeverPublishesAPartialBatch() {
        withModel(model -> {
            model.setNotation("100d6"); model.setTimes("100");
            assertTrue(model.roll()); model.cancelRoll();
        });
        settled();
        assertTrue(state().history.isEmpty());
        assertTrue(diskHistory().batches.isEmpty());
    }

    @Test public void saveFailureRemainsVisibleThroughInvalidSubmissionUntilRetry() throws Exception {
        AtomicReference<File> directory = new AtomicReference<>();
        withActivity(activity -> directory.set(activity.getFilesDir()));
        File blocker = new File(directory.get(), "roll-history.json.new");
        assertFalse(blocker.exists());
        assertTrue(blocker.mkdir());
        try {
            roll("d1+2", "1", RollMode.NORMAL);
            List<RollBatch> unsaved = state().history;
            assertTrue(state().hasUnsavedChanges);
            show("unsaved_warning");
            onView(withTagValue(is("unsaved_warning"))).check(matches(isDisplayed()));
            withModel(model -> { model.setNotation("invalid"); assertFalse(model.roll()); });
            assertTrue(state().hasUnsavedChanges);
            assertEquals(unsaved, state().history);
            assertTrue(blocker.delete());
            clickTag("retry_save");
            settled();
            assertFalse(state().hasUnsavedChanges);
            assertEquals(unsaved, new HistoryStore(directory.get()).load().batches);
        } finally {
            if (blocker.exists()) assertTrue(blocker.isDirectory() && blocker.delete());
            withModel(DiceRollerViewModel::retrySave);
            settled();
        }
    }

    @Test public void corruptHistoryResetRequiresConfirmationAndKeepsExactBackup() throws Exception {
        AtomicReference<File> directory = new AtomicReference<>();
        withActivity(activity -> directory.set(activity.getFilesDir()));
        File history = new File(directory.get(), "roll-history.json");
        File backupDirectory = new File(directory.get(), "history-backups");
        Set<String> oldBackups = new HashSet<>();
        if (backupDirectory.list() != null) oldBackups.addAll(Arrays.asList(backupDirectory.list()));
        byte[] damaged = "Unreadable history / 历史损坏\n".getBytes(StandardCharsets.UTF_8);
        Files.write(history.toPath(), damaged);
        try {
            withModel(DiceRollerViewModel::retryLoad);
            settled();
            assertEquals(HistoryLoadIssue.CORRUPT_FILE, state().loadIssue);
            onView(withTagValue(is("roll_button"))).check(matches(not(isEnabled())));
            clickTag("reset_history");
            onView(withId(android.R.id.button2)).perform(click());
            assertArrayEquals(damaged, Files.readAllBytes(history.toPath()));
            clickTag("reset_history");
            clickDialogTag("confirm_reset");
            settled();
            File[] created = backupDirectory.listFiles(file -> !oldBackups.contains(file.getName()));
            assertNotNull(created);
            assertEquals(1, created.length);
            assertArrayEquals(damaged, Files.readAllBytes(created[0].toPath()));
            assertTrue(new HistoryStore(directory.get()).load().batches.isEmpty());
            assertFalse(state().blockedForRecovery);
        } finally {
            withModel(model -> { if (model.getState().blockedForRecovery) model.resetHistory(); });
            settled();
        }
    }

    @Test public void rollButtonStaysAboveKeyboard() {
        clickTag("notation");
        waitUntil(() -> {
            AtomicReference<Boolean> visible = new AtomicReference<>(false);
            withActivity(activity -> {
                WindowInsets insets = activity.getWindow().getDecorView().getRootWindowInsets();
                visible.set(insets != null && insets.isVisible(WindowInsets.Type.ime()));
            });
            return visible.get();
        }, "Keyboard did not open");
        onView(withTagValue(is("roll_button"))).check(matches(isDisplayed()));
        withActivity(activity -> {
            View decor = activity.getWindow().getDecorView();
            View button = find(activity, "roll_button");
            int[] buttonPosition = new int[2];
            button.getLocationInWindow(buttonPosition);
            int keyboardTop = decor.getHeight() - decor.getRootWindowInsets().getInsets(WindowInsets.Type.ime()).bottom;
            assertTrue("Roll button overlaps keyboard", buttonPosition[1] + button.getHeight() <= keyboardTop + 1);
        });
        Espresso.closeSoftKeyboard();
    }

    @Test public void invalidInputReceivesFocusFromBottomOfHistory() {
        for (int index = 0; index < 10; index++) roll("d1", "1", RollMode.NORMAL);
        withModel(model -> model.setNotation("invalid"));
        show("history_" + state().history.get(state().history.size() - 1).id);
        clickTag("roll_button");
        onView(withTagValue(is("notation"))).check(matches(isFocused())).check(matches(isDisplayed()));
    }

    @Test public void recreationKeepsVisibleHistoryBatch() {
        for (int index = 0; index < 10; index++) roll("d1", "1", RollMode.NORMAL);
        String tag = "history_" + state().history.get(6).id;
        show(tag);
        onView(withTagValue(is(tag))).check(matches(isDisplayed()));
        activityRule.getScenario().recreate();
        settled();
        onView(withTagValue(is(tag))).check(matches(isDisplayed()));
    }

    @Test public void rotationKeepsHistoryAnchorAndShowsSeparateFormWhenWide() throws Exception {
        for (int index = 0; index < 10; index++) roll("d1", "1", RollMode.NORMAL);
        String tag = "history_" + state().history.get(6).id;
        show(tag);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        int previousRotation = Settings.System.getInt(context.getContentResolver(), Settings.System.USER_ROTATION);
        int previousAutoRotation = Settings.System.getInt(context.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION);
        AtomicReference<Integer> initialOrientation = new AtomicReference<>();
        AtomicReference<Integer> displayRotation = new AtomicReference<>();
        withActivity(activity -> {
            initialOrientation.set(activity.getResources().getConfiguration().orientation);
            displayRotation.set(activity.getDisplay().getRotation());
        });
        boolean naturalPortrait = (displayRotation.get() == Surface.ROTATION_0 || displayRotation.get() == Surface.ROTATION_180)
                == (initialOrientation.get() == Configuration.ORIENTATION_PORTRAIT);
        UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            assertTrue(automation.setRotation(naturalPortrait ? UiAutomation.ROTATION_FREEZE_90 : UiAutomation.ROTATION_FREEZE_0));
            waitForOrientation(Configuration.ORIENTATION_LANDSCAPE);
            onView(withTagValue(is(tag))).check(matches(isDisplayed()));
            withActivity(activity -> {
                if (activity.getResources().getConfiguration().screenWidthDp >= 720) {
                    assertTrue(find(activity, "form_list").isShown());
                    assertTrue(find(activity, "main_list").isShown());
                }
            });
            assertTrue(automation.setRotation(naturalPortrait ? UiAutomation.ROTATION_FREEZE_0 : UiAutomation.ROTATION_FREEZE_90));
            waitForOrientation(Configuration.ORIENTATION_PORTRAIT);
            onView(withTagValue(is(tag))).check(matches(isDisplayed()));
        } finally {
            try {
                assertTrue(automation.setRotation(previousRotation));
            } finally {
                if (previousAutoRotation == 1) assertTrue(automation.setRotation(UiAutomation.ROTATION_UNFREEZE));
            }
            waitForOrientation(initialOrientation.get());
        }
    }

    private void waitForOrientation(int orientation) {
        waitUntil(() -> {
            AtomicReference<Integer> current = new AtomicReference<>();
            withActivity(activity -> current.set(activity.getResources().getConfiguration().orientation));
            return current.get() == orientation;
        }, "Requested orientation was not applied");
        settled();
    }
}
