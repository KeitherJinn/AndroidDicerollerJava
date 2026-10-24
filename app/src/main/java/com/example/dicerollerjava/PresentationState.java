package com.example.dicerollerjava;

import android.os.Bundle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Presentation keys may outlive their records unless they follow history's whole-batch retention. */
public final class PresentationState {
    private PresentationState() { }

    /** Call only after loading finishes, so the initial empty snapshot cannot erase restoration state. */
    public static void prune(Bundle presentation, List<RollBatch> history) {
        Set<String> live = new HashSet<>();
        for (RollBatch batch : history) {
            live.add("batch_expanded_" + batch.id);
            live.add("batch_page_" + batch.id);
            for (RollResult result : batch.results) {
                live.add("result_expanded_" + result.id);
                live.add("group_" + result.id + "_first_page");
                if (result.secondRoll != null) live.add("group_" + result.id + "_second_page");
            }
        }
        for (String key : new ArrayList<>(presentation.keySet())) {
            if (isResultKey(key) && !live.contains(key)) presentation.remove(key);
        }
    }

    private static boolean isResultKey(String key) {
        return key.startsWith("batch_expanded_") || key.startsWith("batch_page_")
                || key.startsWith("result_expanded_")
                || key.startsWith("group_") && (key.endsWith("_first_page") || key.endsWith("_second_page"));
    }
}
