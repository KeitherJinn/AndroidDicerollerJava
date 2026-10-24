package com.example.dicerollerjava;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import static org.junit.Assert.*;

/** Runs Android AtomicFile and JSON behavior on the locally installed test device. */
public class HistoryStoreTest {
    private File directory;
    private HistoryStore store;
    @Before public void prepare() throws IOException {
        directory = new File(context().getCacheDir(), "java-history-test-" + UUID.randomUUID());
        if (!directory.mkdirs()) throw new IOException("Cannot create isolated test directory.");
        store = new HistoryStore(directory);
    }
    @After public void cleanup() throws IOException { if (directory != null) deleteTree(directory); }
    private void deleteTree(File file) throws IOException {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        if (file.exists() && !file.delete()) throw new IOException("Cannot remove test file " + file);
    }
    private Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    private RollBatch batch(String id, int count, String notation, RollMode mode) {
        AtomicInteger sequence = new AtomicInteger();
        DiceEngine engine = new DiceEngine(new Random(9), () -> 1234L, () -> id + "-" + sequence.incrementAndGet());
        RollRequest request = DiceEngine.validateRequest("攻击 🎲", notation, Integer.toString(count), mode).request;
        assertNotNull(request);
        List<RollResult> results = new ArrayList<>();
        for (int i = 0; i < count; i++) results.add(engine.roll(request));
        return new RollBatch(id, results);
    }
    private RollBatch batch(String id) { return batch(id, 1, "d1", RollMode.NORMAL); }
    private JSONObject legacyRecord(RollResult result) throws Exception {
        JSONObject record = new JSONObject().put("id", result.id).put("name", result.name).put("notation", result.notation)
                .put("mode", result.mode.name()).put("first", new JSONArray(result.firstRoll.values)).put("timestamp", result.timestamp);
        if (result.secondRoll != null) record.put("second", new JSONArray(result.secondRoll.values));
        return record;
    }
    private String legacySource(List<RollResult> results) throws Exception {
        JSONArray records = new JSONArray();
        for (RollResult result : results) records.put(legacyRecord(result));
        return new JSONObject().put("version", 1).put("rolls", records).toString();
    }
    private File historyFile() { return new File(directory, "roll-history.json"); }
    private void write(String text) throws IOException { Files.write(historyFile().toPath(), text.getBytes(StandardCharsets.UTF_8)); }
    private String read() throws IOException { return new String(Files.readAllBytes(historyFile().toPath()), StandardCharsets.UTF_8); }
    private File singleBackup() {
        File[] files = new File(directory, "history-backups").listFiles();
        assertNotNull(files); assertEquals(1, files.length); return files[0];
    }
    private List<RollResult> flatten(List<RollBatch> batches) {
        return batches.stream().flatMap(batch -> batch.results.stream()).collect(Collectors.toList());
    }
    @Test public void roundTripPreservesCompleteBatchesLongResultsBothGroupsAndUnicode() throws Exception {
        List<RollBatch> batches = Arrays.asList(batch("new", 3, "d1+2147483647", RollMode.ADVANTAGE), batch("old"));
        store.save(batches);
        HistoryLoadResult loaded = new HistoryStore(directory).load();
        assertNull(loaded.issue); assertEquals(batches, loaded.batches); assertEquals(6442450944L, loaded.batches.get(0).total);
        assertEquals(2147483648L, loaded.batches.get(0).results.get(0).finalResult); assertEquals(2, new JSONObject(read()).getInt("version"));
    }
    @Test public void persistedHistoryEvictsWholeBatchesAndClearSurvivesReload() throws Exception {
        List<RollBatch> batches = new ArrayList<>();
        for (int i = 0; i < RollLimits.MAX_BATCHES + 5; i++) batches.add(batch("b" + i, 2, "d1", RollMode.NORMAL));
        store.save(batches);
        List<RollBatch> loaded = new HistoryStore(directory).load().batches;
        assertEquals(100, loaded.size()); assertEquals("b0", loaded.get(0).id); assertEquals("b99", loaded.get(99).id);
        assertTrue(loaded.stream().allMatch(value -> value.count == 2));
        store.save(Collections.emptyList()); assertTrue(new HistoryStore(directory).load().batches.isEmpty());
    }
    @Test public void missingFileMeansEmptyHistory() throws Exception {
        assertEquals(new HistoryLoadResult(Collections.emptyList()), store.load());
    }
    @Test public void legacyVersionMigratesEachRecordToOneBatchWithoutGuessingGroups() throws Exception {
        List<RollResult> results = new ArrayList<>(batch("old", 3, "d1", RollMode.NORMAL).results); Collections.reverse(results);
        String source = legacySource(results); write(source);
        HistoryLoadResult loaded = store.load(); assertNull(loaded.issue);
        assertEquals(results.stream().map(value -> value.id).collect(Collectors.toList()), loaded.batches.stream().map(value -> value.id).collect(Collectors.toList()));
        assertEquals(results, flatten(loaded.batches)); assertTrue(loaded.batches.stream().allMatch(value -> value.count == 1));
        assertEquals(source, read()); store.save(loaded.batches); assertEquals(2, new JSONObject(read()).getInt("version"));
    }
    @Test public void fullLegacyHistoryKeepsAllOneHundredRecordsAfterFirstV2Save() throws Exception {
        List<RollResult> results = new ArrayList<>(batch("legacy", 100, "d1", RollMode.NORMAL).results); Collections.reverse(results);
        write(legacySource(results)); HistoryLoadResult migrated = store.load();
        assertNull(migrated.issue); assertEquals(100, migrated.batches.size()); assertEquals(results, flatten(migrated.batches));
        store.save(migrated.batches); HistoryLoadResult restored = new HistoryStore(directory).load();
        assertNull(restored.issue); assertEquals(100, restored.batches.size()); assertEquals(migrated.batches, restored.batches);
        assertEquals(results, flatten(restored.batches)); assertEquals(2, new JSONObject(read()).getInt("version"));
    }
    @Test public void densestLegalLegacyHistoryPreservesBothGroupsThroughMigration() throws Exception {
        AtomicInteger sequence = new AtomicInteger();
        DiceEngine engine = new DiceEngine(new Random(), () -> 8L, () -> "dense-" + sequence.incrementAndGet());
        RollRequest request = DiceEngine.validateRequest("Old campaign", "1000d1", "1", RollMode.ADVANTAGE).request;
        List<RollResult> results = new ArrayList<>();
        for (int i = 0; i < 100; i++) results.add(0, engine.roll(request));
        write(legacySource(results)); HistoryLoadResult migrated = store.load();
        assertNull(migrated.issue); assertEquals(100, migrated.batches.size());
        assertEquals(200000, migrated.batches.stream().mapToInt(value -> value.individualDiceCount).sum());
        assertEquals(results, flatten(migrated.batches)); store.save(migrated.batches);
        HistoryLoadResult restored = new HistoryStore(directory).load();
        assertNull(restored.issue); assertEquals(migrated.batches, restored.batches);
        assertEquals(200000, restored.batches.stream().mapToInt(value -> value.individualDiceCount).sum());
    }
    @Test public void oneBadLegacyRecordKeepsOthersButProtectsOriginalUntilRecovery() throws Exception {
        RollResult first = batch("a").results.get(0), last = batch("b").results.get(0);
        String source = new JSONObject().put("version", 1).put("rolls", new JSONArray()
                .put(legacyRecord(first)).put(new JSONObject().put("id", "broken")).put(legacyRecord(last))).toString();
        write(source); HistoryLoadResult loaded = store.load();
        assertEquals(HistoryLoadIssue.RECOVERED_RECORDS, loaded.issue); assertEquals(1, loaded.skippedRecords);
        assertEquals(Arrays.asList(first, last), flatten(loaded.batches));
        assertThrows(IllegalStateException.class, () -> store.save(loaded.batches)); assertEquals(source, read());
        store.recover(loaded.batches);
        assertArrayEquals(source.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(singleBackup().toPath()));
        assertEquals(loaded.batches, store.load().batches); assertNull(store.load().issue);
    }
    @Test public void invalidResultDiscardsEntireV2BatchAndNeverInventsPartialStatistics() throws Exception {
        RollBatch bad = batch("bad", 3, "d1", RollMode.NORMAL), good = batch("good", 2, "d1", RollMode.NORMAL);
        store.save(Arrays.asList(bad, good)); JSONObject source = new JSONObject(read());
        source.getJSONArray("batches").getJSONObject(0).getJSONArray("results").getJSONObject(1).put("first", new JSONArray().put(99));
        write(source.toString()); HistoryLoadResult loaded = store.load();
        assertEquals(HistoryLoadIssue.RECOVERED_RECORDS, loaded.issue); assertEquals(1, loaded.skippedRecords);
        assertEquals(Collections.singletonList(good), loaded.batches); assertThrows(IllegalStateException.class, () -> store.save(Collections.emptyList()));
    }
    @Test public void fractionalDieValueIsRejectedInsteadOfRoundedIntoResult() throws Exception {
        store.save(Collections.singletonList(batch("fraction"))); JSONObject source = new JSONObject(read());
        source.getJSONArray("batches").getJSONObject(0).getJSONArray("results").getJSONObject(0).put("first", new JSONArray().put(1.5));
        write(source.toString()); HistoryLoadResult loaded = store.load();
        assertEquals(HistoryLoadIssue.RECOVERED_RECORDS, loaded.issue); assertTrue(loaded.batches.isEmpty());
    }
    @Test public void overflowingNumericNotationDiscardsDamagedWholeBatchesAndProtectsSource() throws Exception {
        RollBatch good = batch("valid");
        store.save(Arrays.asList(batch("overflow-count"), batch("overflow-modifier"), good));
        JSONObject source = new JSONObject(read());
        JSONArray batches = source.getJSONArray("batches");
        batches.getJSONObject(0).getJSONArray("results").getJSONObject(0).put("notation", "2147483648d6");
        batches.getJSONObject(1).getJSONArray("results").getJSONObject(0).put("notation", "d6+999999999999999999999");
        String original = source.toString(); write(original);
        HistoryLoadResult loaded = store.load();
        assertEquals(HistoryLoadIssue.RECOVERED_RECORDS, loaded.issue); assertEquals(2, loaded.skippedRecords);
        assertEquals(Collections.singletonList(good), loaded.batches);
        assertThrows(IllegalStateException.class, () -> store.save(loaded.batches)); assertEquals(original, read());
        store.recover(loaded.batches);
        assertArrayEquals(original.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(singleBackup().toPath()));
        assertEquals(Collections.singletonList(good), store.load().batches); assertNull(store.load().issue);
    }
    @Test public void unknownVersionCannotBeOverwrittenEvenByNewRepositoryInstance() throws Exception {
        String source = "{\"version\":99,\"future\":\"must remain intact\"}"; write(source);
        assertEquals(HistoryLoadIssue.UNSUPPORTED_VERSION, store.load().issue);
        assertThrows(IllegalStateException.class, () -> store.save(Collections.emptyList()));
        assertThrows(IllegalStateException.class, () -> new HistoryStore(directory).save(Collections.emptyList())); assertEquals(source, read());
        store.recover(Collections.emptyList()); assertArrayEquals(source.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(singleBackup().toPath()));
        assertEquals(new HistoryLoadResult(Collections.emptyList()), store.load());
    }
    @Test public void corruptFileIsBackedUpByteForByteBeforeExplicitReset() throws Exception {
        byte[] source = new byte[]{0, (byte) 0xff, 5, 23, 34, 96}; Files.write(historyFile().toPath(), source);
        assertEquals(HistoryLoadIssue.CORRUPT_FILE, store.load().issue);
        assertThrows(IllegalStateException.class, () -> store.save(Collections.emptyList())); store.recover(Collections.emptyList());
        assertArrayEquals(source, Files.readAllBytes(singleBackup().toPath())); assertEquals(new HistoryLoadResult(Collections.emptyList()), store.load());
    }
    @Test public void failedBackupDoesNotOverwriteOriginalOrUnlockOrdinarySave() throws Exception {
        String source = "not valid json"; write(source);
        Files.write(new File(directory, "history-backups").toPath(), "Blocking file".getBytes(StandardCharsets.UTF_8));
        store.load(); assertThrows(IOException.class, () -> store.recover(Collections.emptyList()));
        assertEquals(source, read()); assertThrows(IllegalStateException.class, () -> store.save(Collections.emptyList()));
    }
    @Test public void oversizedSourceIsProtectedAndEntireOriginalIsBackedUp() throws Exception {
        byte[] source = new byte[8 * 1024 * 1024 + 5]; Arrays.fill(source, (byte) 65); Files.write(historyFile().toPath(), source);
        assertEquals(HistoryLoadIssue.CORRUPT_FILE, store.load().issue);
        assertThrows(IllegalStateException.class, () -> store.save(Collections.emptyList())); store.recover(Collections.emptyList());
        assertArrayEquals(source, Files.readAllBytes(singleBackup().toPath())); assertNull(store.load().issue);
    }
    @Test public void retryLoadReevaluatesProtectionAfterSourceBecomesReadable() throws Exception {
        write("{"); assertEquals(HistoryLoadIssue.CORRUPT_FILE, store.load().issue);
        write("{\"version\":2,\"batches\":[]}"); assertNull(store.load().issue);
        List<RollBatch> saved = Collections.singletonList(batch("retried")); store.save(saved); assertEquals(saved, store.load().batches);
    }
    @Test public void ioReadFailureIsThrownInsteadOfReportedAsEmptyHistory() throws Exception {
        assertTrue(historyFile().mkdir());
        assertThrows(IOException.class, store::load); assertThrows(IOException.class, () -> store.save(Collections.emptyList()));
    }
    @Test public void duplicateResultIdsCannotEnterHistoryOrOverwriteARecoverySource() throws Exception {
        RollBatch original = batch("one"), duplicate = new RollBatch("two", original.results);
        write("bad source"); store.load();
        assertThrows(IllegalArgumentException.class, () -> store.recover(Arrays.asList(original, duplicate)));
        assertEquals("bad source", read()); assertFalse(new File(directory, "history-backups").exists());
    }
    @Test public void chineseAndEnglishResourcesAndPluralsAreAvailable() {
        Resources chinese = localized(Locale.SIMPLIFIED_CHINESE), english = localized(Locale.US);
        assertEquals("常用骰子", chinese.getString(R.string.quick_dice)); assertEquals("Quick dice", english.getString(R.string.quick_dice));
        assertEquals("已完成 2 次投掷。", chinese.getQuantityString(R.plurals.rolls_completed, 2, 2));
        assertEquals("1 roll completed.", english.getQuantityString(R.plurals.rolls_completed, 1, 1));
        assertEquals("2 rolls completed.", english.getQuantityString(R.plurals.rolls_completed, 2, 2));
    }
    @Test public void englishAndChineseHelpKeepCompleteParseableExamples() {
        List<String> expected = Arrays.asList("d20", "2d6+3", "4d8-2");
        List<DiceExpression> expectedDice = Arrays.asList(new DiceExpression(1, 20, 0), new DiceExpression(2, 6, 3), new DiceExpression(4, 8, -2));
        Pattern token = Pattern.compile("[0-9]*[dD][0-9]+[^\\s,，、.。:：;；]*");
        for (Locale locale : new Locale[]{Locale.US, Locale.SIMPLIFIED_CHINESE}) {
            Resources resources = localized(locale);
            for (int id : new int[]{R.string.notation_hint, R.string.error_notation}) {
                String rendered = resources.getString(id, expected.toArray());
                Matcher matcher = token.matcher(rendered); List<String> examples = new ArrayList<>();
                while (matcher.find()) examples.add(matcher.group());
                assertEquals(rendered, expected, examples);
                assertEquals(rendered, expectedDice, examples.stream().map(DiceEngine::parseDiceNotation).collect(Collectors.toList()));
            }
        }
    }
    private Resources localized(Locale locale) {
        Configuration configuration = new Configuration(context().getResources().getConfiguration()); configuration.setLocale(locale);
        return context().createConfigurationContext(configuration).getResources();
    }
}
