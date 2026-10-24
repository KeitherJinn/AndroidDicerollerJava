package com.example.dicerollerjava;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable screen snapshot. Derived flags share the same snapshot as their inputs. */
public final class RollerUiState {
    public final String name;
    public final String notation;
    public final String timesText;
    public final RollMode mode;
    public final List<RollBatch> history;
    public final boolean isLoading;
    public final boolean isRolling;
    public final boolean isSaving;
    public final int progress;
    public final int requested;
    public final InputIssue notationIssue;
    public final InputIssue timesIssue;
    public final RollStatus status;
    public final int resultRevision;
    public final boolean hasUnsavedChanges;
    public final HistoryLoadIssue loadIssue;
    public final boolean loadFailed;
    public final int skippedRecords;
    public final boolean clearFailed;
    public final boolean recoveryFailed;
    public final boolean busy;
    public final boolean blockedForRecovery;

    public RollerUiState() { this(new Builder()); }

    private RollerUiState(Builder b) {
        name = b.name;
        notation = b.notation;
        timesText = b.timesText;
        mode = b.mode;
        history = Collections.unmodifiableList(new ArrayList<>(b.history));
        isLoading = b.isLoading;
        isRolling = b.isRolling;
        isSaving = b.isSaving;
        progress = b.progress;
        requested = b.requested;
        notationIssue = b.notationIssue;
        timesIssue = b.timesIssue;
        status = b.status;
        resultRevision = b.resultRevision;
        hasUnsavedChanges = b.hasUnsavedChanges;
        loadIssue = b.loadIssue;
        loadFailed = b.loadFailed;
        skippedRecords = b.skippedRecords;
        clearFailed = b.clearFailed;
        recoveryFailed = b.recoveryFailed;
        busy = isLoading || isRolling || isSaving;
        blockedForRecovery = loadIssue != null || loadFailed;
    }

    public Builder copy() { return new Builder(this); }

    public static final class Builder {
        public String name = "";
        public String notation = "d20";
        public String timesText = "1";
        public RollMode mode = RollMode.NORMAL;
        public List<RollBatch> history = Collections.emptyList();
        public boolean isLoading = true;
        public boolean isRolling;
        public boolean isSaving;
        public int progress;
        public int requested;
        public InputIssue notationIssue;
        public InputIssue timesIssue;
        public RollStatus status;
        public int resultRevision;
        public boolean hasUnsavedChanges;
        public HistoryLoadIssue loadIssue;
        public boolean loadFailed;
        public int skippedRecords;
        public boolean clearFailed;
        public boolean recoveryFailed;

        public Builder() { }

        private Builder(RollerUiState s) {
            name = s.name;
            notation = s.notation;
            timesText = s.timesText;
            mode = s.mode;
            history = s.history;
            isLoading = s.isLoading;
            isRolling = s.isRolling;
            isSaving = s.isSaving;
            progress = s.progress;
            requested = s.requested;
            notationIssue = s.notationIssue;
            timesIssue = s.timesIssue;
            status = s.status;
            resultRevision = s.resultRevision;
            hasUnsavedChanges = s.hasUnsavedChanges;
            loadIssue = s.loadIssue;
            loadFailed = s.loadFailed;
            skippedRecords = s.skippedRecords;
            clearFailed = s.clearFailed;
            recoveryFailed = s.recoveryFailed;
        }

        public RollerUiState build() { return new RollerUiState(this); }
    }
}
