package com.example.dicerollerjava;

import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Versioned bounded history; damaged data remains locked until explicit backed-up recovery. */
public final class HistoryStore implements HistoryRepository {
    private static final int MAX_FILE_BYTES = 8 * 1024 * 1024;
    private final File directory;
    private final AtomicFile file;
    private boolean loaded;
    private boolean requiresRecovery = true;

    public HistoryStore(File directory) {
        this.directory = directory;
        this.file = new AtomicFile(new File(directory, "roll-history.json"));
    }

    @Override public synchronized HistoryLoadResult load() throws IOException {
        // Any read failure keeps an earlier damaged source protected.
        loaded = false;
        requiresRecovery = true;
        byte[] bytes = readSource();
        HistoryLoadResult result;
        if (bytes == null) result = new HistoryLoadResult(Collections.emptyList());
        else if (bytes.length > MAX_FILE_BYTES) result = new HistoryLoadResult(Collections.emptyList(), HistoryLoadIssue.CORRUPT_FILE);
        else {
            try { result = decode(new JSONObject(new String(bytes, StandardCharsets.UTF_8))); }
            catch (JSONException | IllegalArgumentException error) {
                result = new HistoryLoadResult(Collections.emptyList(), HistoryLoadIssue.CORRUPT_FILE);
            }
        }
        loaded = true;
        requiresRecovery = result.issue != null;
        return result;
    }

    private byte[] readSource() throws IOException {
        try (FileInputStream input = file.openRead()) {
            return input.readNBytes(MAX_FILE_BYTES + 1);
        } catch (FileNotFoundException error) {
            if (sourceExists()) throw error;
            return null;
        }
    }

    private boolean sourceExists() {
        return file.getBaseFile().exists() || new File(file.getBaseFile().getPath() + ".bak").exists();
    }

    private HistoryLoadResult decode(JSONObject root) throws JSONException {
        long version = exactLong(root.get("version"));
        if (version != 1 && version != 2) return new HistoryLoadResult(Collections.emptyList(), HistoryLoadIssue.UNSUPPORTED_VERSION);
        JSONArray records = root.getJSONArray(version == 1 ? "rolls" : "batches");
        List<RollBatch> batches = new ArrayList<>();
        Set<String> batchIds = new HashSet<>(), resultIds = new HashSet<>();
        int skipped = 0;
        for (int index = 0; index < records.length(); index++) {
            RollBatch batch;
            try {
                if (version == 1) {
                    RollResult result = readResult(records.getJSONObject(index));
                    batch = new RollBatch(result.id, Collections.singletonList(result));
                } else {
                    JSONObject source = records.getJSONObject(index);
                    JSONArray rolls = source.getJSONArray("results");
                    require(rolls.length() >= 1 && rolls.length() <= RollLimits.MAX_REPEATS);
                    List<RollResult> results = new ArrayList<>();
                    for (int j = 0; j < rolls.length(); j++) results.add(readResult(rolls.getJSONObject(j)));
                    batch = new RollBatch(stringValue(source, "id"), results);
                }
                require(!batchIds.contains(batch.id));
                for (RollResult result : batch.results) require(!resultIds.contains(result.id));
            } catch (JSONException | IllegalArgumentException error) { batch = null; }
            if (batch == null) skipped++;
            else {
                batches.add(batch); batchIds.add(batch.id);
                for (RollResult result : batch.results) resultIds.add(result.id);
            }
        }
        return new HistoryLoadResult(RollBatch.keepCompleteBatches(batches),
                skipped == 0 ? null : HistoryLoadIssue.RECOVERED_RECORDS, skipped);
    }

    private RollResult readResult(JSONObject record) throws JSONException {
        DiceExpression dice = DiceEngine.parseDiceNotation(stringValue(record, "notation"));
        require(dice != null);
        RollMode mode = RollMode.valueOf(stringValue(record, "mode"));
        if (mode == RollMode.NORMAL) require(!record.has("second") || record.isNull("second"));
        String id = stringValue(record, "id"), name = stringValue(record, "name");
        require(!DiceEngine.trimWhitespace(id).isEmpty() && id.length() <= 64);
        require(name.length() <= RollLimits.MAX_NAME_LENGTH);
        return new RollResult(id, name, dice.notation, mode, readRoll(record, "first", dice),
                mode == RollMode.NORMAL ? null : readRoll(record, "second", dice), dice.modifier, exactLong(record.get("timestamp")));
    }

    private DiceRoll readRoll(JSONObject record, String key, DiceExpression dice) throws JSONException {
        JSONArray source = record.getJSONArray(key);
        require(source.length() == dice.numberOfDice);
        List<Integer> values = new ArrayList<>();
        for (int index = 0; index < source.length(); index++) {
            long value = exactLong(source.get(index));
            require(value >= 1 && value <= dice.sides);
            values.add((int) value);
        }
        return new DiceRoll(values);
    }

    @Override public synchronized void save(List<RollBatch> batches) throws IOException {
        if (!loaded) load();
        if (requiresRecovery) throw new IllegalStateException("History must be recovered or reset before saving.");
        writeBytes(encode(batches));
    }

    @Override public synchronized void recover(List<RollBatch> batches) throws IOException {
        // Validate replacement data before touching the original or its backup.
        byte[] bytes = encode(batches);
        backupOriginal();
        writeBytes(bytes);
        loaded = true;
        requiresRecovery = false;
    }

    private void backupOriginal() throws IOException {
        FileInputStream input;
        try { input = file.openRead(); }
        catch (FileNotFoundException error) {
            if (sourceExists()) throw error;
            return;
        }
        try (FileInputStream source = input) {
            File backups = new File(directory, "history-backups");
            if (!backups.isDirectory() && !backups.mkdirs()) throw new IOException("Cannot create history backup directory.");
            File backup = File.createTempFile("roll-history-", ".json", backups);
            try (FileOutputStream output = new FileOutputStream(backup)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = source.read(buffer)) != -1) output.write(buffer, 0, count);
                output.getFD().sync();
            } catch (IOException | RuntimeException error) {
                if (!backup.delete()) error.addSuppressed(new IOException("Incomplete backup could not be removed."));
                throw error;
            }
        }
    }

    private byte[] encode(List<RollBatch> batches) {
        try {
            JSONArray records = new JSONArray();
            Set<String> resultIds = new HashSet<>();
            for (RollBatch batch : RollBatch.keepCompleteBatches(batches)) {
                JSONArray rolls = new JSONArray();
                for (RollResult result : batch.results) {
                    require(resultIds.add(result.id));
                    JSONObject record = new JSONObject().put("id", result.id).put("name", result.name)
                            .put("notation", result.notation).put("mode", result.mode.name())
                            .put("first", new JSONArray(result.firstRoll.values)).put("timestamp", result.timestamp);
                    if (result.secondRoll != null) record.put("second", new JSONArray(result.secondRoll.values));
                    rolls.put(record);
                }
                records.put(new JSONObject().put("id", batch.id).put("results", rolls));
            }
            byte[] bytes = new JSONObject().put("version", 2).put("batches", records).toString().getBytes(StandardCharsets.UTF_8);
            require(bytes.length <= MAX_FILE_BYTES);
            return bytes;
        } catch (JSONException error) { throw new IllegalArgumentException("Cannot encode history.", error); }
    }

    private void writeBytes(byte[] bytes) throws IOException {
        FileOutputStream output = file.startWrite();
        try {
            output.write(bytes); output.getFD().sync(); file.finishWrite(output);
        } catch (IOException | RuntimeException error) { file.failWrite(output); throw error; }
    }

    private static String stringValue(JSONObject object, String key) throws JSONException {
        Object value = object.get(key); require(value instanceof String); return (String) value;
    }
    private static long exactLong(Object value) {
        require(value instanceof Integer || value instanceof Long); return ((Number) value).longValue();
    }
    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("Invalid history data.");
    }
}
