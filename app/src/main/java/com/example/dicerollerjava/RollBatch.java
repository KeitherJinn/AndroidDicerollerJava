package com.example.dicerollerjava;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Complete batches retain original roll order and are evicted as a whole. */
public final class RollBatch {
    public final String id, notation, name;
    public final List<RollResult> results;
    public final RollMode mode;
    public final long timestamp, total, minimum, maximum;
    public final int count, individualDiceCount;
    public final double average;
    public RollBatch(String id, List<RollResult> results) {
        if (id == null || DiceEngine.trimWhitespace(id).isEmpty() || id.length() > 64) throw new IllegalArgumentException("Invalid batch ID.");
        this.id = id;
        this.results = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(results)));
        count = this.results.size();
        if (count < 1 || count > RollLimits.MAX_REPEATS) throw new IllegalArgumentException("Invalid batch size.");
        RollResult first = this.results.get(0);
        DiceExpression dice = DiceEngine.parseDiceNotation(first.notation);
        if (dice == null || first.name.length() > RollLimits.MAX_NAME_LENGTH) throw new IllegalArgumentException("Invalid batch expression or name.");
        notation = first.notation; name = first.name; mode = first.mode; timestamp = first.timestamp;
        Set<String> ids = new HashSet<>();
        long runningTotal = 0, low = Long.MAX_VALUE, high = Long.MIN_VALUE;
        int diceCount = 0;
        for (RollResult result : this.results) {
            if (DiceEngine.trimWhitespace(result.id).isEmpty() || result.id.length() > 64 || !ids.add(result.id)
                    || !result.notation.equals(notation) || !result.name.equals(name) || result.mode != mode
                    || result.modifier != dice.modifier) throw new IllegalArgumentException("Inconsistent batch result.");
            validateRoll(result.firstRoll, dice);
            if (mode == RollMode.NORMAL) {
                if (result.secondRoll != null) throw new IllegalArgumentException("Unexpected second group.");
            } else validateRoll(result.secondRoll, dice);
            runningTotal += result.finalResult;
            low = Math.min(low, result.finalResult); high = Math.max(high, result.finalResult);
            diceCount += result.firstRoll.values.size() + (result.secondRoll == null ? 0 : result.secondRoll.values.size());
        }
        if (diceCount > RollLimits.MAX_TOTAL_DICE) throw new IllegalArgumentException("Batch work limit exceeded.");
        total = runningTotal; minimum = low; maximum = high; individualDiceCount = diceCount; average = (double) total / count;
    }
    private static void validateRoll(DiceRoll roll, DiceExpression dice) {
        if (roll == null || roll.values.size() != dice.numberOfDice) throw new IllegalArgumentException("Invalid roll length.");
        for (int value : roll.values) if (value < 1 || value > dice.sides) throw new IllegalArgumentException("Invalid die value.");
    }
    public static List<RollBatch> keepCompleteBatches(List<RollBatch> batches) {
        List<RollBatch> retained = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int resultCount = 0, diceCount = 0;
        for (RollBatch batch : batches) {
            if (!ids.add(batch.id)) continue;
            if (retained.size() == RollLimits.MAX_BATCHES || resultCount + batch.count > RollLimits.MAX_HISTORY_RESULTS ||
                    diceCount + batch.individualDiceCount > RollLimits.MAX_HISTORY_DICE) break;
            retained.add(batch); resultCount += batch.count; diceCount += batch.individualDiceCount;
        }
        return Collections.unmodifiableList(retained);
    }
    public static List<RollBatch> mergeBatches(List<RollBatch> newestFirst, List<RollBatch> previous) {
        List<RollBatch> combined = new ArrayList<>(newestFirst); combined.addAll(previous);
        return keepCompleteBatches(combined);
    }
    @Override public boolean equals(Object other) {
        if (!(other instanceof RollBatch)) return false;
        RollBatch value = (RollBatch) other;
        return id.equals(value.id) && results.equals(value.results);
    }
    @Override public int hashCode() { return Objects.hash(id, results); }
}
