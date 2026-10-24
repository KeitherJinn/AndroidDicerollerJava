package com.example.dicerollerjava;

import android.os.Bundle;
import android.os.Parcel;
import android.view.View;
import android.view.ViewGroup;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import static org.junit.Assert.*;

/**
 * The same test APK can measure the original and fixed application APKs. No new production API
 * is referenced here. Pass -e structureBaseline true only when explicitly measuring the old APK.
 * Timings are observations, never flaky pass/fail thresholds; identity and retained state are asserted.
 */
public class StructuralRiskUiTest extends BaseUiTest {
    private static final int EXPANDED_BATCHES = 20;

    @Test public void maximumHistoryRenderingKeepsUnchangedCards() {
        List<RollBatch> batches = maximumHistory();
        assertEquals(100, batches.size());
        assertEquals(1000, batches.stream().mapToInt(batch -> batch.count).sum());
        assertEquals(200000, batches.stream().mapToInt(batch -> batch.individualDiceCount).sum());
        withActivity(activity -> {
            Bundle presentation = presentation(activity);
            for (int index = 0; index < EXPANDED_BATCHES; index++) {
                RollBatch batch = batches.get(index);
                presentation.putBoolean("batch_expanded_" + batch.id, true);
                presentation.putBoolean("result_expanded_" + batch.results.get(0).id, true);
            }
            long start = System.nanoTime();
            installState(activity, snapshot(batches, false));
            long renderNanos = System.nanoTime() - start;
            long layoutNanos = measureAndLayout(activity);
            metric("initial", "renderMs", millis(renderNanos), "measureLayoutMs", millis(layoutNanos),
                    "batches", batches.size(), "results", 1000, "individualDice", 200000,
                    "expandedBatches", EXPANDED_BATCHES, "views", viewCount(root(activity)));

            RollBatch selected = batches.get(5);
            // A result detail toggle and a value-page change must rebuild only their owning batch.
            checkMutation(activity, batches, "details", 99,
                    () -> find(activity, "result_details_" + selected.results.get(0).id).performClick());
            find(activity, "result_details_" + selected.results.get(0).id).performClick();
            checkMutation(activity, batches, "valuePage", 99,
                    () -> find(activity, "group_" + selected.results.get(0).id + "_first_next").performClick());
            checkMutation(activity, batches, "busyStart", 100,
                    () -> installState(activity, snapshot(batches, true)));
            assertFalse(find(activity, "load_batch_" + selected.id).isEnabled());
            assertFalse(find(activity, "load_result_" + selected.results.get(0).id).isEnabled());
            checkMutation(activity, batches, "busyEnd", 100,
                    () -> installState(activity, snapshot(batches, false)));
            assertTrue(find(activity, "load_batch_" + selected.id).isEnabled());
            assertTrue(find(activity, "load_result_" + selected.results.get(0).id).isEnabled());
            checkMutation(activity, batches, "batchPage", 99,
                    () -> find(activity, "batch_" + selected.id + "_next").performClick());
            assertNotNull(find(activity, "load_result_" + selected.results.get(10).id));
        });
    }

    @Test public void retiredPresentationKeysDoNotGrowSavedState() {
        List<RollBatch> batches = maximumHistory();
        withActivity(activity -> {
            Bundle original = new Bundle(presentation(activity));
            try {
            installState(activity, snapshot(batches, false));
            Bundle presentation = presentation(activity);
            presentation.putBoolean("advanced", true);
            presentation.putInt("formScrollY", 123);
            presentation.putString("future_setting", "preserve unrelated keys");
            // Every supported live presentation key is retained, including results not on the first page.
            for (RollBatch batch : batches) {
                presentation.putBoolean("batch_expanded_" + batch.id, true);
                presentation.putInt("batch_page_" + batch.id, 0);
                for (RollResult result : batch.results) {
                    presentation.putBoolean("result_expanded_" + result.id, true);
                    presentation.putInt("group_" + result.id + "_first_page", 1);
                    presentation.putInt("group_" + result.id + "_second_page", 1);
                }
            }
            int start = 0;
            for (int retiredCount : new int[] {500, 2000, 5000}) {
                for (int index = start; index < retiredCount; index++) {
                    String batch = retiredId("a", index), result = retiredId("b", index);
                    presentation.putBoolean("batch_expanded_" + batch, true);
                    presentation.putInt("batch_page_" + batch, 1);
                    presentation.putBoolean("result_expanded_" + result, true);
                    presentation.putInt("group_" + result + "_first_page", 1);
                    presentation.putInt("group_" + result + "_second_page", 1);
                }
                start = retiredCount;
                int beforeKeys = presentation.size(), beforeBytes = parcelBytes(presentation);
                Bundle saved = new Bundle();
                invoke(activity, "onSaveInstanceState", new Class<?>[] {Bundle.class}, saved);
                Bundle retained = saved.getBundle("presentation");
                assertNotNull(retained);
                metric("savedState", "retiredBatches", retiredCount, "beforeKeys", beforeKeys,
                        "beforeParcelBytes", beforeBytes, "afterKeys", retained.size(),
                        "afterParcelBytes", parcelBytes(retained), "wholeSavedStateBytes", parcelBytes(saved));
                assertEquals("preserve unrelated keys", retained.getString("future_setting"));
                assertEquals(123, retained.getInt("formScrollY"));
                for (RollBatch batch : batches) {
                    assertTrue(retained.getBoolean("batch_expanded_" + batch.id));
                    assertTrue(retained.containsKey("batch_page_" + batch.id));
                    for (RollResult result : batch.results) {
                        assertTrue(retained.getBoolean("result_expanded_" + result.id));
                        assertEquals(1, retained.getInt("group_" + result.id + "_first_page"));
                        assertEquals(1, retained.getInt("group_" + result.id + "_second_page"));
                    }
                }
                assertEquals(baseline(), retained.containsKey("batch_expanded_" + retiredId("a", 0)));
                if (!baseline()) assertTrue("Only live result keys and unrelated settings may remain", retained.size() < 3220);
            }
            // Clear/eviction cleanup also occurs during normal rendering, not just on save.
            installState(activity, snapshot(Collections.emptyList(), false));
            if (!baseline()) {
                assertFalse(presentation.containsKey("batch_expanded_" + batches.get(0).id));
                assertFalse(presentation.containsKey("result_expanded_" + batches.get(0).results.get(0).id));
            }
            } finally {
                // The old APK must not send our intentionally oversized synthetic Bundle to Android
                // during ActivityScenario teardown. Parcel measurements above require no Binder crash.
                Bundle presentation = presentation(activity);
                presentation.clear(); presentation.putAll(original);
                installState(activity, snapshot(Collections.emptyList(), false));
            }
        });
    }

    @Test public void loadingKeepsPresentationUntilHistoryIsKnown() {
        List<RollBatch> batches = maximumHistory();
        String key = "batch_expanded_" + batches.get(0).id;
        withActivity(activity -> {
            presentation(activity).putBoolean(key, true);
            installState(activity, new RollerUiState());
            Bundle saved = new Bundle();
            invoke(activity, "onSaveInstanceState", new Class<?>[] {Bundle.class}, saved);
            assertTrue(saved.getBundle("presentation").getBoolean(key));
            installState(activity, snapshot(batches, false));
            assertTrue(presentation(activity).getBoolean(key));
            installState(activity, snapshot(Collections.emptyList(), false));
            assertEquals(baseline(), presentation(activity).containsKey(key));
        });
    }

    private void checkMutation(MainActivity activity, List<RollBatch> batches, String name,
                               int expectedRetained, Runnable change) {
        Map<String, View> before = cards(activity, batches);
        long start = System.nanoTime(); change.run(); long renderNanos = System.nanoTime() - start;
        long layoutNanos = measureAndLayout(activity);
        Map<String, View> after = cards(activity, batches);
        int same = 0;
        for (String id : before.keySet()) if (before.get(id) == after.get(id)) same++;
        metric(name, "renderMs", millis(renderNanos), "measureLayoutMs", millis(layoutNanos),
                "retainedCards", same, "replacedCards", batches.size() - same,
                "views", viewCount(root(activity)));
        assertEquals("Unrelated cards must keep identity for " + name, baseline() ? 0 : expectedRetained, same);
    }

    private Map<String, View> cards(MainActivity activity, List<RollBatch> batches) {
        Map<String, View> result = new LinkedHashMap<>();
        for (int index = 0; index < batches.size(); index++) {
            RollBatch batch = batches.get(index);
            result.put(batch.id, find(activity, index == 0 ? "latest_result" : "history_" + batch.id));
        }
        return result;
    }

    private static RollerUiState snapshot(List<RollBatch> history, boolean busy) {
        RollerUiState.Builder state = new RollerUiState.Builder();
        state.isLoading = false; state.history = history; state.isSaving = busy;
        return state.build();
    }

    private static void installState(MainActivity activity, RollerUiState state) {
        try {
            Field field = DiceRollerViewModel.class.getDeclaredField("state"); field.setAccessible(true);
            field.set(activity.getModel(), state);
            invoke(activity, "render", new Class<?>[] {RollerUiState.class}, state);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static Bundle presentation(MainActivity activity) { return (Bundle) field(activity, "presentation"); }
    private static View root(MainActivity activity) { return (View) field(activity, "root"); }
    private static Object field(MainActivity activity, String name) {
        try {
            Field field = MainActivity.class.getDeclaredField(name); field.setAccessible(true); return field.get(activity);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void invoke(MainActivity activity, String name, Class<?>[] types, Object... args) {
        try {
            Method method = MainActivity.class.getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(activity, args);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static long measureAndLayout(MainActivity activity) {
        View view = root(activity);
        long start = System.nanoTime();
        view.measure(View.MeasureSpec.makeMeasureSpec(view.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(view.getHeight(), View.MeasureSpec.EXACTLY));
        view.layout(view.getLeft(), view.getTop(), view.getRight(), view.getBottom());
        return System.nanoTime() - start;
    }
    private static int viewCount(View view) {
        int count = 1;
        if (view instanceof ViewGroup) for (int index = 0; index < ((ViewGroup) view).getChildCount(); index++)
            count += viewCount(((ViewGroup) view).getChildAt(index));
        return count;
    }
    private static int parcelBytes(Bundle state) {
        Parcel parcel = Parcel.obtain();
        try { parcel.writeBundle(state); return parcel.dataSize(); } finally { parcel.recycle(); }
    }
    private static boolean baseline() {
        return "true".equals(InstrumentationRegistry.getArguments().getString("structureBaseline"));
    }
    private static double millis(long nanos) { return nanos / 1_000_000.0; }
    private static String retiredId(String prefix, int index) {
        return prefix + String.format(Locale.ROOT, "%035d", index);
    }
    private static void metric(String operation, Object... values) {
        try {
            JSONObject result = new JSONObject(); result.put("baseline", baseline()); result.put("operation", operation);
            for (int index = 0; index < values.length; index += 2) result.put((String) values[index], values[index + 1]);
            Bundle status = new Bundle(); status.putString("stream", "\nSTRUCTURE_METRIC " + result + "\n");
            InstrumentationRegistry.getInstrumentation().sendStatus(0, status);
        } catch (org.json.JSONException error) { throw new AssertionError(error); }
    }
    private static List<RollBatch> maximumHistory() {
        List<RollBatch> batches = new ArrayList<>();
        DiceRoll first = new DiceRoll(Collections.nCopies(100, 1));
        for (int batch = 0; batch < 100; batch++) {
            List<RollResult> results = new ArrayList<>();
            int resultCount = batch == 5 ? 20 : batch >= 90 ? 9 : 10;
            for (int result = 0; result < resultCount; result++) results.add(new RollResult("live_r_" + batch + "_" + result,
                    "Rendering fixture", "100d1", RollMode.ADVANTAGE, first, first, 0, 1000 + batch));
            batches.add(new RollBatch("live_b_" + batch, results));
        }
        return batches;
    }
}
