package com.example.dicerollerjava;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class HistoryLoadResult {
    public final List<RollBatch> batches;
    public final HistoryLoadIssue issue;
    /** Number of invalid legacy records or whole invalid v2 batches. */
    public final int skippedRecords;
    public HistoryLoadResult(List<RollBatch> batches, HistoryLoadIssue issue, int skippedRecords) {
        this.batches = Collections.unmodifiableList(new ArrayList<>(batches));
        this.issue = issue; this.skippedRecords = skippedRecords;
    }
    public HistoryLoadResult(List<RollBatch> batches) { this(batches, null, 0); }
    public HistoryLoadResult(List<RollBatch> batches, HistoryLoadIssue issue) { this(batches, issue, 0); }
    @Override public boolean equals(Object other) {
        if (!(other instanceof HistoryLoadResult)) return false;
        HistoryLoadResult value = (HistoryLoadResult) other;
        return batches.equals(value.batches) && issue == value.issue && skippedRecords == value.skippedRecords;
    }
    @Override public int hashCode() { return Objects.hash(batches, issue, skippedRecords); }
}
