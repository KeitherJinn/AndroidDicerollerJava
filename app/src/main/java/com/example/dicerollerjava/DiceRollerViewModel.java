package com.example.dicerollerjava;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Java state owner: CPU work and disk writes never run on the Android main thread. */
public final class DiceRollerViewModel extends ViewModel {
    private final SavedStateHandle saved;
    private final HistoryRepository repository;
    private final DiceEngine engine;
    private final Executor computationExecutor;
    private final Executor ioExecutor;
    private final Executor mainExecutor;
    private final boolean ownsExecutors;
    private final List<Consumer<RollerUiState>> observers = new ArrayList<>();
    private volatile RollerUiState state;
    private boolean notationTooLong;
    private boolean timesTooLong;
    private boolean closed;
    private RollTask rollTask;

    public DiceRollerViewModel(SavedStateHandle saved, HistoryRepository repository) {
        this(saved, repository, new DiceEngine(), Executors.newSingleThreadExecutor(),
                Executors.newSingleThreadExecutor(), mainThreadExecutor(), true);
    }

    public DiceRollerViewModel(SavedStateHandle saved, HistoryRepository repository,
                              DiceEngine engine, Executor computationExecutor,
                              Executor ioExecutor, Executor mainExecutor) {
        this(saved, repository, engine, computationExecutor, ioExecutor, mainExecutor, false);
    }

    private DiceRollerViewModel(SavedStateHandle saved, HistoryRepository repository,
                               DiceEngine engine, Executor computationExecutor,
                               Executor ioExecutor, Executor mainExecutor, boolean ownsExecutors) {
        this.saved = saved;
        this.repository = repository;
        this.engine = engine;
        this.computationExecutor = computationExecutor;
        this.ioExecutor = ioExecutor;
        this.mainExecutor = mainExecutor;
        this.ownsExecutors = ownsExecutors;
        notationTooLong = Boolean.TRUE.equals(saved.get("notationTooLong"));
        timesTooLong = Boolean.TRUE.equals(saved.get("timesTooLong"));
        RollerUiState.Builder initial = new RollerUiState.Builder();
        initial.name = savedText("name", "");
        initial.notation = savedText("notation", "d20");
        initial.timesText = savedText("times", "1");
        try { initial.mode = RollMode.valueOf(savedText("mode", "NORMAL")); }
        catch (IllegalArgumentException ignored) { initial.mode = RollMode.NORMAL; }
        initial.notationIssue = notationTooLong ? InputIssue.NOTATION_LENGTH : null;
        initial.timesIssue = timesTooLong ? InputIssue.REPEAT_LIMIT : null;
        state = initial.build();
        readHistory();
    }

    private static Executor mainThreadExecutor() {
        Handler handler = new Handler(Looper.getMainLooper());
        return command -> handler.post(command);
    }

    private String savedText(String key, String fallback) {
        Object value = saved.get(key);
        return value instanceof String ? (String) value : fallback;
    }

    public RollerUiState getState() { return state; }

    /** Subscribe on the UI thread; invoke the returned action when the view is destroyed. */
    public synchronized Runnable observe(Consumer<RollerUiState> observer) {
        if (closed) return () -> { };
        observers.add(observer);
        observer.accept(state);
        return () -> {
            synchronized (DiceRollerViewModel.this) { observers.remove(observer); }
        };
    }

    private void update(Consumer<RollerUiState.Builder> change) {
        updateThen(change, () -> { });
    }

    /** Queue work before exposing its busy state: an observer may synchronously close the owner. */
    private void updateThen(Consumer<RollerUiState.Builder> change, Runnable beforeNotify) {
        if (closed) return;
        RollerUiState.Builder next = state.copy();
        change.accept(next);
        state = next.build();
        beforeNotify.run();
        for (Consumer<RollerUiState> observer : new ArrayList<>(observers)) {
            if (closed) break;
            if (observers.contains(observer)) observer.accept(state);
        }
    }

    private void onMain(Runnable action) {
        mainExecutor.execute(() -> {
            synchronized (DiceRollerViewModel.this) {
                if (!closed) action.run();
            }
        });
    }

    private void readHistory() {
        ioExecutor.execute(() -> {
            try {
                HistoryLoadResult loaded = repository.load();
                onMain(() -> update(b -> {
                    b.history = loaded.batches;
                    b.isLoading = false;
                    b.loadFailed = false;
                    b.loadIssue = loaded.issue;
                    b.skippedRecords = loaded.skippedRecords;
                    b.recoveryFailed = false;
                    b.clearFailed = false;
                    b.status = loaded.issue == null ? null : RollStatus.LOAD_FAILED;
                }));
            } catch (Exception error) {
                onMain(() -> update(b -> {
                    b.isLoading = false;
                    b.loadFailed = true;
                    b.loadIssue = null;
                    b.status = RollStatus.LOAD_FAILED;
                }));
            }
        });
    }

    public synchronized void setName(String value) {
        if (closed || state.busy) return;
        String accepted = value.substring(0, Math.min(value.length(), RollLimits.MAX_NAME_LENGTH));
        saved.set("name", accepted);
        update(b -> b.name = accepted);
    }

    public synchronized void setNotation(String value) {
        if (closed || state.busy || notationTooLong && value.equals(state.notation)) return;
        notationTooLong = value.length() > RollLimits.MAX_NOTATION_LENGTH;
        saved.set("notationTooLong", notationTooLong);
        if (notationTooLong) {
            // Reject the entire edit: truncating an expression could change its meaning.
            update(b -> b.notationIssue = InputIssue.NOTATION_LENGTH);
            return;
        }
        saved.set("notation", value);
        update(b -> {
            b.notation = value;
            b.notationIssue = null;
            b.timesIssue = timesTooLong ? InputIssue.REPEAT_LIMIT : null;
        });
    }

    public synchronized void setTimes(String value) {
        if (closed || state.busy || timesTooLong && value.equals(state.timesText)) return;
        timesTooLong = value.length() > 10;
        saved.set("timesTooLong", timesTooLong);
        if (timesTooLong) {
            update(b -> b.timesIssue = InputIssue.REPEAT_LIMIT);
            return;
        }
        saved.set("times", value);
        update(b -> { b.timesText = value; b.timesIssue = null; });
    }

    public synchronized void setMode(RollMode mode) {
        if (closed || state.busy) return;
        saved.set("mode", mode.name());
        update(b -> { b.mode = mode; b.timesIssue = timesTooLong ? InputIssue.REPEAT_LIMIT : null; });
    }

    public synchronized void setQuickDie(int sides) {
        if (closed || state.busy) return;
        if (sides != 4 && sides != 6 && sides != 8 && sides != 10 && sides != 12 && sides != 20 && sides != 100) return;
        notationTooLong = false;
        setNotation("d" + sides);
    }

    public synchronized void loadBatch(RollBatch batch) { loadSettings(batch.results.get(0), batch.count); }

    public synchronized void loadResult(RollResult result) { loadSettings(result, 1); }

    private void loadSettings(RollResult result, int count) {
        if (closed || state.busy) return;
        notationTooLong = false;
        timesTooLong = false;
        setName(result.name);
        setNotation(result.notation);
        setTimes(Integer.toString(count));
        setMode(result.mode);
        update(b -> b.status = null);
    }

    /** False leaves validation/recovery details in the current state for the UI to present. */
    public synchronized boolean roll() {
        if (closed || state.busy || state.blockedForRecovery) return false;
        RequestValidation validation = DiceEngine.validateRequest(state.name, state.notation, state.timesText, state.mode);
        InputIssue notationIssue = notationTooLong ? InputIssue.NOTATION_LENGTH : validation.notationIssue;
        InputIssue timesIssue = timesTooLong ? InputIssue.REPEAT_LIMIT : validation.timesIssue;
        update(b -> { b.notationIssue = notationIssue; b.timesIssue = timesIssue; b.status = null; });
        if (closed || notationIssue != null || timesIssue != null || validation.request == null) return false;
        RollRequest request = validation.request;
        RollTask task = new RollTask();
        rollTask = task;
        updateThen(b -> { b.isRolling = true; b.progress = 0; b.requested = request.times; },
                () -> computationExecutor.execute(() -> computeRoll(task, request)));
        return true;
    }

    private void computeRoll(RollTask task, RollRequest request) {
        try {
            List<RollResult> results = new ArrayList<>();
            for (int index = 0; index < request.times; index++) {
                task.checkActive();
                results.add(engine.roll(request, task::checkActive));
                int progress = index + 1;
                onMain(() -> {
                    if (rollTask == task && !task.cancelled.get() && state.isRolling) update(b -> b.progress = progress);
                });
            }
            task.checkActive();
            RollBatch batch = new RollBatch(results.get(0).id, results);
            onMain(() -> commitRoll(task, batch));
        } catch (CancellationException ignored) {
            // Also handle a worker interrupted independently of the UI's cancel action.
            onMain(() -> {
                if (rollTask != task) return;
                rollTask = null;
                update(b -> { b.isRolling = false; b.status = RollStatus.CANCELLED; });
            });
        } catch (Exception error) {
            onMain(() -> {
                if (rollTask != task || task.cancelled.get()) return;
                rollTask = null;
                update(b -> { b.isRolling = false; b.status = RollStatus.ROLL_FAILED; });
            });
        }
    }

    private void commitRoll(RollTask task, RollBatch batch) {
        if (rollTask != task || task.cancelled.get() || !state.isRolling) return;
        List<RollBatch> history = RollBatch.mergeBatches(Collections.singletonList(batch), state.history);
        rollTask = null;
        // Once the bounded write begins, cancellation/Activity destruction cannot discard it.
        updateThen(b -> { b.isRolling = false; b.isSaving = true; },
                () -> writeHistory(history, false, persisted -> update(b -> {
                    b.history = history;
                    b.isSaving = false;
                    b.hasUnsavedChanges = !persisted;
                    b.status = persisted ? RollStatus.COMPLETED : RollStatus.SAVE_FAILED;
                    b.resultRevision++;
                })));
    }

    public synchronized void cancelRoll() {
        if (closed || !state.isRolling || rollTask == null) return;
        rollTask.cancelled.set(true);
        rollTask = null;
        update(b -> { b.isRolling = false; b.status = RollStatus.CANCELLED; });
    }

    private void writeHistory(List<RollBatch> history, boolean recover, Consumer<Boolean> completed) {
        ioExecutor.execute(() -> {
            boolean success;
            try {
                if (recover) repository.recover(history); else repository.save(history);
                success = true;
            } catch (Exception error) { success = false; }
            boolean result = success;
            onMain(() -> completed.accept(result));
        });
    }

    public synchronized void retrySave() {
        if (closed || state.busy || state.blockedForRecovery || !state.hasUnsavedChanges) return;
        List<RollBatch> history = state.history;
        updateThen(b -> { b.isSaving = true; b.status = null; },
                () -> writeHistory(history, false, persisted -> update(b -> {
                    b.isSaving = false;
                    b.hasUnsavedChanges = !persisted;
                    b.status = persisted ? RollStatus.SAVED : RollStatus.SAVE_FAILED;
                })));
    }

    public synchronized void retryLoad() {
        if (closed || state.busy || state.hasUnsavedChanges) return;
        updateThen(b -> { b.isLoading = true; b.status = null; }, this::readHistory);
    }

    public synchronized void recoverHistory() {
        if (closed || state.busy || state.loadIssue != HistoryLoadIssue.RECOVERED_RECORDS || state.history.isEmpty()) return;
        resolveHistory(state.history, RollStatus.RECOVERED);
    }

    /** The UI must confirm the reset before invoking this operation. */
    public synchronized void resetHistory() {
        if (closed || state.busy || !state.blockedForRecovery) return;
        resolveHistory(Collections.emptyList(), RollStatus.RESET);
    }

    private void resolveHistory(List<RollBatch> history, RollStatus status) {
        updateThen(b -> { b.isSaving = true; b.recoveryFailed = false; b.status = null; },
                () -> writeHistory(history, true, recovered -> update(b -> {
                    b.isSaving = false;
                    b.recoveryFailed = !recovered;
                    if (recovered) {
                        b.history = history;
                        b.loadIssue = null;
                        b.loadFailed = false;
                        b.skippedRecords = 0;
                        b.clearFailed = false;
                        b.hasUnsavedChanges = false;
                        b.status = status;
                    }
                })));
    }

    public synchronized void clearHistory() {
        if (closed || state.busy || state.blockedForRecovery || state.history.isEmpty()) return;
        updateThen(b -> { b.isSaving = true; b.clearFailed = false; b.status = null; },
                () -> writeHistory(Collections.emptyList(), false, cleared -> update(b -> {
                    b.isSaving = false;
                    b.clearFailed = !cleared;
                    if (cleared) { b.history = Collections.emptyList(); b.hasUnsavedChanges = false; }
                    b.status = cleared ? RollStatus.CLEARED : RollStatus.CLEAR_FAILED;
                })));
    }

    /** Acknowledge a transient announcement so rotating the Activity does not repeat it. */
    public synchronized void consumeStatus() {
        if (!closed && state.status != null) update(b -> b.status = null);
    }

    @Override protected synchronized void onCleared() {
        closed = true;
        observers.clear();
        if (rollTask != null) { rollTask.cancelled.set(true); rollTask = null; }
        if (ownsExecutors) {
            ((ExecutorService) computationExecutor).shutdownNow();
            // Graceful shutdown drains any already accepted commit before closing the disk worker.
            ((ExecutorService) ioExecutor).shutdown();
        }
    }

    private static final class RollTask {
        final AtomicBoolean cancelled = new AtomicBoolean();
        void checkActive() {
            if (cancelled.get() || Thread.currentThread().isInterrupted()) throw new CancellationException();
        }
    }
}
