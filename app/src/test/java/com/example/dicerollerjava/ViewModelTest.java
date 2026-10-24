package com.example.dicerollerjava;

import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStore;

import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** Deterministic scheduling makes cancellation and commit boundaries reproducible. */
public class ViewModelTest {
    private final QueuedExecutor computation = new QueuedExecutor();
    private final QueuedExecutor io = new QueuedExecutor();
    private final QueuedExecutor main = new QueuedExecutor();
    private final ViewModelStore models = new ViewModelStore();
    private int nextModel;
    private int nextResult;

    @After public void tearDown() { models.clear(); }

    private static final class QueuedExecutor implements Executor {
        private final Queue<Runnable> queue = new ArrayDeque<>();
        @Override public void execute(Runnable command) { queue.add(command); }
        boolean hasWork() { return !queue.isEmpty(); }
        void runOne() { queue.remove().run(); }
        void runAll() { while (hasWork()) runOne(); }
    }

    private void drain() {
        int steps = 0;
        while (computation.hasWork() || io.hasWork() || main.hasWork()) {
            if (++steps > 10_000) fail("Executor work did not settle");
            computation.runAll();
            io.runAll();
            main.runAll();
        }
    }

    private static final class FakeRepository implements HistoryRepository {
        HistoryLoadResult result;
        IOException loadFailure;
        IOException saveFailure;
        IOException recoverFailure;
        List<RollBatch> persisted;
        int saves;
        int recoveries;

        FakeRepository() { this(Collections.emptyList()); }
        FakeRepository(List<RollBatch> initial) {
            persisted = new ArrayList<>(initial);
            result = new HistoryLoadResult(initial);
        }
        @Override public HistoryLoadResult load() throws IOException {
            if (loadFailure != null) throw loadFailure;
            return result;
        }
        @Override public void save(List<RollBatch> batches) throws IOException {
            saves++;
            if (saveFailure != null) throw saveFailure;
            persisted = new ArrayList<>(batches);
            result = new HistoryLoadResult(persisted);
        }
        @Override public void recover(List<RollBatch> batches) throws IOException {
            recoveries++;
            if (recoverFailure != null) throw recoverFailure;
            persisted = new ArrayList<>(batches);
            result = new HistoryLoadResult(persisted);
        }
    }

    private DiceEngine engine() {
        return new DiceEngine(new Random(42), () -> 1_750_000_000_000L,
                () -> "result-" + ++nextResult);
    }

    private RollBatch batch(int count) {
        RollRequest request = DiceEngine.validateRequest("Attack", "2d6+3", Integer.toString(count), RollMode.ADVANTAGE).request;
        assertNotNull(request);
        DiceEngine engine = engine();
        List<RollResult> results = new ArrayList<>();
        for (int i = 0; i < count; i++) results.add(engine.roll(request));
        return new RollBatch("batch-" + ++nextResult, results);
    }

    private DiceRollerViewModel model() { return model(new FakeRepository()); }
    private DiceRollerViewModel model(FakeRepository repository) {
        return model(repository, new SavedStateHandle(), engine());
    }
    private DiceRollerViewModel model(FakeRepository repository, SavedStateHandle saved, DiceEngine engine) {
        ViewModelProvider provider = new ViewModelProvider(models, new ViewModelProvider.Factory() {
            @Override public <T extends ViewModel> T create(Class<T> modelClass) {
                return modelClass.cast(new DiceRollerViewModel(saved, repository, engine, computation, io, main));
            }
        });
        return provider.get("model-" + ++nextModel, DiceRollerViewModel.class);
    }

    private SavedStateHandle snapshot(SavedStateHandle saved) {
        Map<String, Object> values = new HashMap<>();
        for (String key : saved.keys()) values.put(key, saved.get(key));
        return new SavedStateHandle(values);
    }

    @Test public void failedSaveSurvivesInvalidSubmissionAndCancellationUntilRetrySucceeds() {
        FakeRepository repository = new FakeRepository();
        repository.saveFailure = new IOException("disk unavailable");
        DiceRollerViewModel model = model(repository);
        drain();
        assertTrue(model.roll());
        drain();
        List<RollBatch> unsaved = model.getState().history;
        assertEquals(1, unsaved.size());
        assertTrue(model.getState().hasUnsavedChanges);
        assertTrue(repository.persisted.isEmpty());
        model.setNotation("invalid");
        assertFalse(model.roll());
        assertTrue(model.getState().hasUnsavedChanges);
        model.setNotation("d6");
        assertTrue(model.roll());
        model.cancelRoll();
        drain();
        assertTrue(model.getState().hasUnsavedChanges);
        assertEquals(unsaved, model.getState().history);
        repository.saveFailure = null;
        model.retrySave();
        drain();
        assertFalse(model.getState().hasUnsavedChanges);
        assertEquals(unsaved, repository.persisted);
        assertFalse(model.getState().busy);
        assertEquals(RollStatus.SAVED, model.getState().status);
    }

    @Test public void loadFailureBlocksWritesUntilSuccessfulRetry() {
        List<RollBatch> previous = Collections.singletonList(batch(2));
        FakeRepository repository = new FakeRepository(previous);
        repository.loadFailure = new IOException("read unavailable");
        DiceRollerViewModel model = model(repository);
        drain();
        assertTrue(model.getState().loadFailed);
        assertTrue(model.getState().blockedForRecovery);
        assertFalse(model.roll());
        model.clearHistory();
        model.retrySave();
        drain();
        assertEquals(0, repository.saves);
        assertEquals(previous, repository.persisted);
        repository.loadFailure = null;
        model.retryLoad();
        drain();
        assertFalse(model.getState().loadFailed);
        assertFalse(model.getState().blockedForRecovery);
        assertEquals(previous, model.getState().history);
        assertTrue(model.roll());
        drain();
        assertEquals(2, repository.persisted.size());
    }

    @Test public void recoveredRowsRemainReadOnlyUntilRecoveryBackupSucceeds() {
        List<RollBatch> retained = Collections.singletonList(batch(2));
        FakeRepository repository = new FakeRepository(retained);
        repository.result = new HistoryLoadResult(retained, HistoryLoadIssue.RECOVERED_RECORDS, 1);
        repository.recoverFailure = new IOException("backup unavailable");
        DiceRollerViewModel model = model(repository);
        drain();
        assertEquals(retained, model.getState().history);
        assertEquals(1, model.getState().skippedRecords);
        assertTrue(model.getState().blockedForRecovery);
        assertFalse(model.roll());
        model.recoverHistory();
        drain();
        assertTrue(model.getState().recoveryFailed);
        assertTrue(model.getState().blockedForRecovery);
        assertEquals(retained, model.getState().history);
        assertEquals(0, repository.saves);
        repository.recoverFailure = null;
        model.recoverHistory();
        drain();
        assertEquals(2, repository.recoveries);
        assertFalse(model.getState().blockedForRecovery);
        assertFalse(model.getState().recoveryFailed);
        assertNull(model.getState().loadIssue);
        assertEquals(retained, repository.persisted);
        assertEquals(RollStatus.RECOVERED, model.getState().status);
    }

    @Test public void explicitResetUsesRecoveryPathAndClearsOnlyAfterSuccess() {
        List<RollBatch> retained = Collections.singletonList(batch(2));
        FakeRepository repository = new FakeRepository(retained);
        repository.result = new HistoryLoadResult(retained, HistoryLoadIssue.RECOVERED_RECORDS, 1);
        repository.recoverFailure = new IOException("backup unavailable");
        DiceRollerViewModel model = model(repository);
        drain();
        model.resetHistory();
        drain();
        assertEquals(retained, model.getState().history);
        assertTrue(model.getState().blockedForRecovery);
        repository.recoverFailure = null;
        model.resetHistory();
        drain();
        assertTrue(model.getState().history.isEmpty());
        assertTrue(repository.persisted.isEmpty());
        assertEquals(0, repository.saves);
        assertEquals(2, repository.recoveries);
        assertFalse(model.getState().blockedForRecovery);
        assertEquals(RollStatus.RESET, model.getState().status);
    }

    @Test public void futureFormatCannotBeSilentlyOverwritten() {
        FakeRepository repository = new FakeRepository();
        repository.result = new HistoryLoadResult(Collections.emptyList(), HistoryLoadIssue.UNSUPPORTED_VERSION);
        DiceRollerViewModel model = model(repository);
        drain();
        model.setNotation("d6");
        assertFalse(model.roll());
        model.clearHistory();
        model.retrySave();
        model.recoverHistory();
        drain();
        assertEquals(0, repository.saves);
        assertEquals(0, repository.recoveries);
        assertEquals(HistoryLoadIssue.UNSUPPORTED_VERSION, model.getState().loadIssue);
    }

    @Test public void failedClearPreservesVisibleAndPersistedHistory() {
        List<RollBatch> retained = Collections.singletonList(batch(2));
        FakeRepository repository = new FakeRepository(retained);
        repository.saveFailure = new IOException("write unavailable");
        DiceRollerViewModel model = model(repository);
        drain();
        model.clearHistory();
        drain();
        assertTrue(model.getState().clearFailed);
        assertEquals(retained, model.getState().history);
        assertEquals(retained, repository.persisted);
        assertEquals(RollStatus.CLEAR_FAILED, model.getState().status);
        repository.saveFailure = null;
        model.clearHistory();
        drain();
        assertFalse(model.getState().clearFailed);
        assertTrue(model.getState().history.isEmpty());
        assertTrue(repository.persisted.isEmpty());
    }

    @Test public void oversizedEditCannotSubmitItsPrefixOrPreviousExpressionAfterRestoration() {
        SavedStateHandle saved = new SavedStateHandle();
        FakeRepository repository = new FakeRepository();
        DiceRollerViewModel model = model(repository, saved, engine());
        drain();
        model.setNotation("d1+7");
        model.setNotation("d1" + String.join("", Collections.nCopies(123, " ")) + "+100");
        assertEquals("d1+7", model.getState().notation);
        assertEquals(InputIssue.NOTATION_LENGTH, model.getState().notationIssue);
        model.setNotation(model.getState().notation);
        assertEquals(InputIssue.NOTATION_LENGTH, model.getState().notationIssue);
        model.setTimes("2");
        model.setMode(RollMode.ADVANTAGE);
        assertFalse(model.roll());
        assertEquals(0, repository.saves);
        DiceRollerViewModel restored = model(repository, snapshot(saved), engine());
        drain();
        assertFalse(restored.roll());
        assertEquals(InputIssue.NOTATION_LENGTH, restored.getState().notationIssue);
        restored.setNotation("d1+100");
        assertTrue(restored.roll());
        drain();
        assertEquals(2, restored.getState().history.get(0).count);
        assertEquals(101L, restored.getState().history.get(0).results.get(0).finalResult);
        assertEquals(101L, restored.getState().history.get(0).results.get(1).finalResult);
    }

    @Test public void oversizedRepeatEditStaysInvalidUntilExplicitCorrection() {
        SavedStateHandle saved = new SavedStateHandle();
        DiceRollerViewModel model = model(new FakeRepository(), saved, engine());
        drain();
        model.setTimes("100000000001");
        model.setTimes(model.getState().timesText);
        model.setNotation("d6");
        model.setMode(RollMode.DISADVANTAGE);
        assertEquals("1", model.getState().timesText);
        assertFalse(model.roll());
        assertEquals(InputIssue.REPEAT_LIMIT, model.getState().timesIssue);
        DiceRollerViewModel restored = model(new FakeRepository(), snapshot(saved), engine());
        drain();
        assertFalse(restored.roll());
        restored.setTimes("2");
        assertTrue(restored.roll());
        drain();
        assertEquals(2, restored.getState().history.get(0).count);
    }

    @Test public void savedStateRestoresDraftIntoANewViewModel() {
        SavedStateHandle saved = new SavedStateHandle();
        DiceRollerViewModel first = model(new FakeRepository(), saved, engine());
        drain();
        first.setName("攻击");
        first.setNotation("2d6+3");
        first.setTimes("3");
        first.setMode(RollMode.DISADVANTAGE);
        DiceRollerViewModel restored = model(new FakeRepository(), snapshot(saved), engine());
        drain();
        assertEquals("攻击", restored.getState().name);
        assertEquals("2d6+3", restored.getState().notation);
        assertEquals("3", restored.getState().timesText);
        assertEquals(RollMode.DISADVANTAGE, restored.getState().mode);
        assertTrue(restored.roll());
        drain();
        assertEquals(3, restored.getState().history.get(0).count);
    }

    @Test public void quickDieChangesOnlyExpressionAndDoesNotRoll() {
        DiceRollerViewModel model = model();
        drain();
        model.setName("Attack");
        model.setTimes("3");
        model.setMode(RollMode.DISADVANTAGE);
        model.setNotation("2d20+5");
        model.setQuickDie(6);
        assertEquals(new DiceExpression(1, 6, 0), DiceEngine.parseDiceNotation(model.getState().notation));
        assertEquals("Attack", model.getState().name);
        assertEquals("3", model.getState().timesText);
        assertEquals(RollMode.DISADVANTAGE, model.getState().mode);
        assertTrue(model.getState().history.isEmpty());
        model.setQuickDie(7);
        assertEquals("d6", model.getState().notation);
    }

    @Test public void batchAndSingleResultReuseLoadSettingsWithoutCreatingHistory() {
        RollBatch previous = batch(3);
        FakeRepository repository = new FakeRepository(Collections.singletonList(previous));
        DiceRollerViewModel model = model(repository);
        drain();
        model.loadBatch(previous);
        assertEquals(previous.name, model.getState().name);
        assertEquals(previous.notation, model.getState().notation);
        assertEquals(previous.mode, model.getState().mode);
        assertEquals("3", model.getState().timesText);
        model.loadResult(previous.results.get(1));
        assertEquals("1", model.getState().timesText);
        assertEquals(previous.notation, model.getState().notation);
        assertEquals(Collections.singletonList(previous), model.getState().history);
        assertEquals(0, repository.saves);
    }

    @Test public void cancellingBeforeWorkerStartsDoesNotCreateABatch() {
        FakeRepository repository = new FakeRepository();
        DiceRollerViewModel model = model(repository);
        drain();
        assertTrue(model.roll());
        model.cancelRoll();
        assertFalse(model.getState().busy);
        drain();
        assertEquals(RollStatus.CANCELLED, model.getState().status);
        assertEquals(0, repository.saves);
        assertTrue(model.getState().history.isEmpty());
    }

    @Test public void cancellingInsideGroupNeverPublishesOrPersistsAPartialBatch() {
        List<RollBatch> previous = Collections.singletonList(batch(2));
        FakeRepository repository = new FakeRepository(previous);
        DiceRollerViewModel[] holder = new DiceRollerViewModel[1];
        AtomicInteger generated = new AtomicInteger();
        Random random = new Random(0) {
            @Override public int nextInt(int bound) {
                if (generated.incrementAndGet() == 50) holder[0].cancelRoll();
                return 0;
            }
        };
        holder[0] = model(repository, new SavedStateHandle(),
                new DiceEngine(random, () -> 1000L, () -> "cancel-result"));
        DiceRollerViewModel model = holder[0];
        drain();
        model.setNotation("100d6");
        model.setTimes("100");
        assertTrue(model.roll());
        drain();
        assertTrue(generated.get() >= 50 && generated.get() <= 100);
        assertEquals(RollStatus.CANCELLED, model.getState().status);
        assertEquals(previous, model.getState().history);
        assertEquals(previous, repository.persisted);
        assertEquals(0, repository.saves);
        assertFalse(model.getState().busy);
    }

    @Test public void cancellationAfterComputationButBeforeCommitStillDiscardsTheBatch() {
        FakeRepository repository = new FakeRepository();
        DiceRollerViewModel model = model(repository);
        drain();
        assertTrue(model.roll());
        computation.runAll();
        assertTrue(model.getState().isRolling);
        model.cancelRoll();
        drain();
        assertEquals(0, repository.saves);
        assertTrue(model.getState().history.isEmpty());
        assertEquals(RollStatus.CANCELLED, model.getState().status);
    }

    @Test public void cancellationCannotInterruptAnAcceptedCommit() {
        FakeRepository repository = new FakeRepository();
        DiceRollerViewModel model = model(repository);
        drain();
        assertTrue(model.roll());
        computation.runAll();
        main.runAll();
        assertFalse(model.getState().isRolling);
        assertTrue(model.getState().isSaving);
        model.cancelRoll();
        drain();
        assertEquals(1, repository.saves);
        assertEquals(repository.persisted, model.getState().history);
        assertEquals(RollStatus.COMPLETED, model.getState().status);
        assertFalse(model.getState().busy);
    }

    @Test public void viewModelClearLeavesAnAcceptedCommitQueuedAndRemovesObservers() {
        FakeRepository repository = new FakeRepository();
        DiceRollerViewModel model = model(repository);
        drain();
        AtomicInteger observations = new AtomicInteger();
        model.observe(state -> observations.incrementAndGet());
        assertTrue(model.roll());
        computation.runAll();
        main.runAll();
        assertTrue(model.getState().isSaving);
        int beforeClear = observations.get();
        models.clear();
        drain();
        assertEquals(1, repository.saves);
        assertEquals(1, repository.persisted.size());
        assertEquals(beforeClear, observations.get());
        assertFalse(model.roll());
    }

    @Test public void viewModelClearCancelsComputationBeforeAnyWrite() {
        FakeRepository repository = new FakeRepository();
        DiceRollerViewModel model = model(repository);
        drain();
        model.roll();
        models.clear();
        drain();
        assertEquals(0, repository.saves);
        assertTrue(repository.persisted.isEmpty());
    }

    @Test public void closingOwnerFromSavingObserverStillKeepsTheAcceptedCommit() {
        FakeRepository repository = new FakeRepository();
        DiceRollerViewModel model = model(repository);
        drain();
        AtomicInteger savingNotifications = new AtomicInteger();
        model.observe(state -> {
            if (state.isSaving) {
                savingNotifications.incrementAndGet();
                assertTrue("The write must be accepted before an observer can close its owner", io.hasWork());
                models.clear();
            }
        });
        assertTrue(model.roll());
        drain();
        assertEquals(1, savingNotifications.get());
        assertEquals(1, repository.saves);
        assertEquals(1, repository.persisted.size());
    }

    @Test public void closingOwnerFromRollingObserverCancelsAlreadyQueuedWork() {
        FakeRepository repository = new FakeRepository();
        DiceRollerViewModel model = model(repository);
        drain();
        model.observe(state -> {
            if (state.isRolling) {
                assertTrue("Queue work before lifecycle callbacks may shut down its executor", computation.hasWork());
                models.clear();
            }
        });
        assertTrue(model.roll());
        drain();
        assertEquals(0, repository.saves);
        assertTrue(repository.persisted.isEmpty());
    }

    @Test public void workerCancellationWithoutUiActionReturnsToIdleAndPreservesHistory() {
        List<RollBatch> retained = Collections.singletonList(batch(2));
        FakeRepository repository = new FakeRepository(retained);
        DiceEngine interrupted = new DiceEngine() {
            @Override public RollResult roll(RollRequest request, Runnable checkpoint) {
                throw new CancellationException("worker interrupted independently");
            }
        };
        DiceRollerViewModel model = model(repository, new SavedStateHandle(), interrupted);
        drain();
        assertTrue(model.roll());
        drain();
        assertFalse(model.getState().busy);
        assertEquals(RollStatus.CANCELLED, model.getState().status);
        assertEquals(retained, model.getState().history);
        assertEquals(retained, repository.persisted);
        assertEquals(0, repository.saves);
    }

    @Test public void loadingProgressAndPersistenceArePublishedOnlyThroughMainExecutor() {
        FakeRepository repository = new FakeRepository();
        DiceRollerViewModel model = model(repository);
        io.runAll();
        assertTrue(model.getState().isLoading);
        main.runAll();
        assertFalse(model.getState().busy);
        model.setTimes("2");
        model.roll();
        computation.runAll();
        assertEquals(0, model.getState().progress);
        assertTrue(model.getState().isRolling);
        main.runAll();
        assertEquals(2, model.getState().progress);
        assertTrue(model.getState().isSaving);
        assertTrue(repository.persisted.isEmpty());
        io.runAll();
        assertEquals(1, repository.persisted.size());
        assertTrue(model.getState().history.isEmpty());
        assertTrue(model.getState().isSaving);
        main.runAll();
        assertEquals(repository.persisted, model.getState().history);
        assertFalse(model.getState().busy);
    }

    @Test public void staleCancelledCallbacksCannotModifyANewRoll() {
        FakeRepository repository = new FakeRepository();
        DiceRollerViewModel model = model(repository);
        drain();
        model.setNotation("d1+1");
        model.roll();
        computation.runAll();
        model.cancelRoll();
        model.setNotation("d1+7");
        model.roll();
        drain();
        assertEquals(1, repository.saves);
        assertEquals(8L, model.getState().history.get(0).results.get(0).finalResult);
        assertEquals(RollStatus.COMPLETED, model.getState().status);
    }

    @Test public void calculationFailureLeavesHistoryIntactAndAllowsRetry() {
        List<RollBatch> retained = Collections.singletonList(batch(2));
        FakeRepository repository = new FakeRepository(retained);
        DiceEngine broken = new DiceEngine() {
            @Override public RollResult roll(RollRequest request, Runnable checkpoint) {
                throw new IllegalStateException("injected engine failure");
            }
        };
        DiceRollerViewModel model = model(repository, new SavedStateHandle(), broken);
        drain();
        assertTrue(model.roll());
        drain();
        assertEquals(RollStatus.ROLL_FAILED, model.getState().status);
        assertFalse(model.getState().busy);
        assertEquals(retained, model.getState().history);
        assertEquals(0, repository.saves);
        assertTrue(model.roll());
        model.cancelRoll();
        drain();
    }

    @Test public void consumedStatusDoesNotReplayToReplacementObserver() {
        DiceRollerViewModel model = model();
        drain();
        model.roll();
        drain();
        assertEquals(RollStatus.COMPLETED, model.getState().status);
        int revision = model.getState().resultRevision;
        model.consumeStatus();
        List<RollStatus> notifications = new ArrayList<>();
        Runnable unsubscribe = model.observe(state -> notifications.add(state.status));
        assertEquals(Collections.singletonList(null), notifications);
        unsubscribe.run();
        model.setName("Changed");
        assertEquals(1, notifications.size());
        assertEquals(revision, model.getState().resultRevision);
    }

    @Test public void editingAndDuplicateActionsAreBlockedWhileBusy() {
        FakeRepository repository = new FakeRepository();
        DiceRollerViewModel model = model(repository);
        model.setNotation("d6");
        assertEquals("d20", model.getState().notation);
        assertFalse(model.roll());
        drain();
        model.roll();
        model.setName("ignored");
        model.setNotation("d6");
        model.setTimes("3");
        model.setMode(RollMode.ADVANTAGE);
        model.setQuickDie(100);
        assertEquals("", model.getState().name);
        assertEquals("d20", model.getState().notation);
        assertEquals("1", model.getState().timesText);
        assertEquals(RollMode.NORMAL, model.getState().mode);
        assertFalse(model.roll());
        drain();
        assertEquals(1, repository.saves);
    }

    @Test public void retryLoadCannotDiscardUnsavedHistory() {
        FakeRepository repository = new FakeRepository();
        repository.saveFailure = new IOException("full");
        DiceRollerViewModel model = model(repository);
        drain();
        model.roll();
        drain();
        List<RollBatch> unsaved = model.getState().history;
        model.retryLoad();
        drain();
        assertTrue(model.getState().hasUnsavedChanges);
        assertEquals(unsaved, model.getState().history);
    }

    @Test public void invalidSavedModeFallsBackToNormalAndSnapshotHistoryIsImmutable() {
        Map<String, Object> values = new HashMap<>();
        values.put("mode", "future-mode");
        values.put("name", 99);
        DiceRollerViewModel model = model(new FakeRepository(), new SavedStateHandle(values), engine());
        drain();
        assertEquals(RollMode.NORMAL, model.getState().mode);
        assertEquals("", model.getState().name);
        try {
            model.getState().history.add(batch(1));
            fail("Screen state history must be immutable");
        } catch (UnsupportedOperationException expected) {
            assertTrue(model.getState().history.isEmpty());
        }
    }
}
