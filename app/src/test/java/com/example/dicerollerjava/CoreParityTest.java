package com.example.dicerollerjava;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import static org.junit.Assert.*;

/** Behavioral equivalents of the Kotlin engine, batch, and export regression suites. */
public class CoreParityTest {
    private RollRequest request(String notation, RollMode mode) {
        RollRequest request = DiceEngine.validateRequest("", notation, "1", mode).request;
        assertNotNull(request);
        return request;
    }
    private static final class SequenceRandom extends Random {
        private final int[] values;
        int count;
        SequenceRandom(int... values) { this.values = values; }
        @Override public int nextInt(int bound) {
            int value = values[count++];
            assertTrue(value >= 1 && value <= bound);
            return value - 1;
        }
    }
    private RollBatch batch(String id, int count, String notation, RollMode mode) {
        AtomicInteger sequence = new AtomicInteger();
        DiceEngine engine = new DiceEngine(new Random(4), () -> 1234L, () -> id + "-" + sequence.incrementAndGet());
        RollRequest request = DiceEngine.validateRequest("Note", notation, Integer.toString(count), mode).request;
        assertNotNull(request);
        List<RollResult> results = new ArrayList<>();
        for (int i = 0; i < count; i++) results.add(engine.roll(request));
        return new RollBatch(id, results);
    }
    private RollResult result(String id, List<Integer> values, List<Integer> second, long modifier, RollMode mode, String name) {
        return new RollResult(id, name, new DiceExpression(values.size(), 1000, modifier).notation, mode,
                new DiceRoll(values), second == null ? null : new DiceRoll(second), modifier, 1_750_000_000_000L);
    }
    @Test public void parsesSupportedExpressionsAndNormalizesWhitespace() {
        assertEquals(new DiceExpression(1, 20, 0), DiceEngine.parseDiceNotation("D20"));
        assertEquals(new DiceExpression(2, 6, 3), DiceEngine.parseDiceNotation(" 2d6 +\t3 "));
        assertEquals(new DiceExpression(4, 8, -2), DiceEngine.parseDiceNotation("4d8-2"));
        assertEquals("1d20", DiceEngine.parseDiceNotation("d20").notation);
    }
    @Test public void rejectsMalformedExpressions() {
        for (String text : new String[]{"", "d", "-1d6", "2d6+", "2d6+1d4", "1.5d6"}) assertNull(text, DiceEngine.parseDiceNotation(text));
    }
    @Test public void fullWidthChineseKeyboardInputIsNormalized() {
        assertEquals(new DiceExpression(2, 6, 3), DiceEngine.parseDiceNotation("２ｄ６＋３"));
        assertEquals(3, DiceEngine.validateRequest("", "２Ｄ６－２", "３", RollMode.NORMAL).request.times);
    }
    @Test public void enforcesDiceAndSideBoundaries() {
        assertNotNull(DiceEngine.parseDiceNotation("1000d1000000"));
        for (String text : new String[]{"0d6", "1001d6", "2147483648d6"}) assertEquals(InputIssue.DICE_LIMIT, DiceEngine.parseDiceInput(text).issue);
        for (String text : new String[]{"d0", "d1000001", "d2147483648"}) assertEquals(InputIssue.SIDES_LIMIT, DiceEngine.parseDiceInput(text).issue);
    }
    @Test public void enforcesModifierBoundaries() {
        assertNotNull(DiceEngine.parseDiceNotation("d1-2147483648"));
        assertNotNull(DiceEngine.parseDiceNotation("d1+2147483647"));
        for (String text : new String[]{"d1+2147483648", "d1-2147483649", "d1+999999999999999999999"}) assertEquals(InputIssue.MODIFIER_LIMIT, DiceEngine.parseDiceInput(text).issue);
    }
    @Test public void longInputIsRejectedBeforeParsing() {
        assertEquals(InputIssue.NOTATION_LENGTH, DiceEngine.parseDiceInput("1".repeat(10000) + "d6").issue);
        assertEquals(InputIssue.NOTATION_LENGTH, DiceEngine.parseDiceInput("d1" + " ".repeat(123) + "+100").issue);
    }
    @Test public void repeatCountHasABoundedPositiveRange() {
        for (String text : new String[]{"", "0", "-1", "101", "2147483647", "2147483648", "1.5"}) assertEquals(text, InputIssue.REPEAT_LIMIT, DiceEngine.validateRequest("", "d20", text, RollMode.NORMAL).timesIssue);
        assertNotNull(DiceEngine.validateRequest("", "d20", " 100 ", RollMode.NORMAL).request);
    }
    @Test public void workLimitIncludesSecondGroup() {
        assertNotNull(DiceEngine.validateRequest("", "1000d6", "10", RollMode.NORMAL).request);
        assertEquals(InputIssue.WORK_LIMIT, DiceEngine.validateRequest("", "1000d6", "11", RollMode.NORMAL).timesIssue);
        assertNotNull(DiceEngine.validateRequest("", "1000d6", "5", RollMode.ADVANTAGE).request);
        assertEquals(InputIssue.WORK_LIMIT, DiceEngine.validateRequest("", "1000d6", "6", RollMode.DISADVANTAGE).timesIssue);
    }
    @Test public void nameIsOptionalTrimmedAndBounded() {
        assertEquals("", request("d1", RollMode.NORMAL).name);
        assertEquals("Attack", DiceEngine.validateRequest(" Attack ", "d20", "1", RollMode.NORMAL).request.name);
        assertEquals("x".repeat(80), DiceEngine.validateRequest("x".repeat(90), "d20", "1", RollMode.NORMAL).request.name);
    }
    @Test public void normalModeRollsOneGroupAndAddsModifierOnce() {
        SequenceRandom random = new SequenceRandom(1, 6);
        RollResult result = new DiceEngine(random, () -> 1234L, () -> "test").roll(request("2d6+3", RollMode.NORMAL));
        assertEquals(2, random.count); assertNull(result.secondRoll); assertEquals(10L, result.finalResult);
        assertEquals(1234L, result.timestamp); assertEquals("test", result.id);
    }
    @Test public void advantageComparesWholeGroupTotals() {
        RollResult result = new DiceEngine(new SequenceRandom(1, 6, 4, 4)).roll(request("2d6+3", RollMode.ADVANTAGE));
        assertEquals(Arrays.asList(4, 4), result.chosenRoll.values); assertEquals(11L, result.finalResult);
    }
    @Test public void disadvantageComparesWholeGroupTotals() {
        RollResult result = new DiceEngine(new SequenceRandom(1, 6, 4, 4)).roll(request("2d6+3", RollMode.DISADVANTAGE));
        assertEquals(Arrays.asList(1, 6), result.chosenRoll.values); assertEquals(10L, result.finalResult);
    }
    @Test public void tiedGroupsChooseFirstGroup() {
        for (RollMode mode : new RollMode[]{RollMode.ADVANTAGE, RollMode.DISADVANTAGE}) {
            RollResult result = new DiceEngine(new SequenceRandom(1, 6, 3, 4)).roll(request("2d6+3", mode));
            assertSame(result.firstRoll, result.chosenRoll);
        }
    }
    @Test public void formerIntegerOverflowProducesCorrectPositiveResult() {
        assertEquals(2147483648L, new DiceEngine().roll(request("d1+2147483647", RollMode.NORMAL)).finalResult);
        assertEquals(-2147483647L, new DiceEngine().roll(request("d1-2147483648", RollMode.NORMAL)).finalResult);
    }
    @Test public void generatedValuesStayInRangeAndSumsMatch() {
        DiceEngine engine = new DiceEngine(new Random(42));
        for (int i = 0; i < 100; i++) {
            RollResult result = engine.roll(request("100d6-2", RollMode.ADVANTAGE));
            for (DiceRoll group : Arrays.asList(result.firstRoll, result.secondRoll)) {
                assertEquals(100, group.values.size());
                assertTrue(group.values.stream().allMatch(value -> value >= 1 && value <= 6));
                assertEquals(group.values.stream().mapToLong(Integer::longValue).sum(), group.sum);
            }
            assertEquals(result.chosenRoll.sum - 2, result.finalResult);
        }
    }
    @Test(expected = CancellationException.class) public void longRollChecksForCancellation() {
        new DiceEngine().roll(request("1000d6", RollMode.NORMAL), () -> { throw new CancellationException(); });
    }
    @Test public void legacyHistoryMergeIsBoundedNewestFirstAndDeduplicated() {
        AtomicInteger id = new AtomicInteger();
        DiceEngine engine = new DiceEngine(new Random(), () -> 1L, () -> Integer.toString(id.incrementAndGet()));
        List<RollResult> earlier = new ArrayList<>(), latest = new ArrayList<>();
        for (int i = 0; i < 100; i++) earlier.add(0, engine.roll(request("d1", RollMode.NORMAL)));
        for (int i = 0; i < 10; i++) latest.add(0, engine.roll(request("d1", RollMode.NORMAL)));
        List<RollResult> merged = DiceEngine.mergeHistory(latest, earlier);
        assertEquals(100, merged.size()); assertEquals("110", merged.get(0).id); assertEquals("11", merged.get(99).id);
        assertEquals(100, DiceEngine.mergeHistory(Collections.singletonList(merged.get(0)), merged).size());
    }
    @Test public void statisticsUseEveryResultAndPreserveChronologicalOrder() {
        List<RollResult> results = new ArrayList<>();
        List<List<Integer>> values = Arrays.asList(Arrays.asList(1, 2), Arrays.asList(6, 6), Arrays.asList(1, 1));
        for (int i = 0; i < values.size(); i++) results.add(new RollResult("r" + i, "Attack", "2d6+3", RollMode.NORMAL, new DiceRoll(values.get(i)), null, 3, 100L + i));
        RollBatch batch = new RollBatch("batch", results);
        assertEquals(Arrays.asList(6L, 15L, 5L), batch.results.stream().map(value -> value.finalResult).collect(Collectors.toList()));
        assertEquals(3, batch.count); assertEquals(26L, batch.total); assertEquals(26.0 / 3, batch.average, 0.000001);
        assertEquals(5L, batch.minimum); assertEquals(15L, batch.maximum); assertEquals("Attack", batch.name);
        assertEquals("2d6+3", batch.notation); assertEquals(RollMode.NORMAL, batch.mode); assertEquals(100L, batch.timestamp); assertEquals(6, batch.individualDiceCount);
    }
    @Test public void totalsRemainLongAcrossOneHundredLargeResults() {
        RollBatch batch = batch("large", 100, "d1+2147483647", RollMode.NORMAL);
        assertEquals(214748364800L, batch.total); assertEquals(2147483648.0, batch.average, 0.0);
    }
    @Test public void groupModesCountBothSetsOfPhysicalDice() {
        assertEquals(10000, batch("double", 5, "1000d1", RollMode.ADVANTAGE).individualDiceCount);
    }
    @Test public void rejectsEmptyMixedDuplicateAndOversizedBatches() {
        assertThrows(IllegalArgumentException.class, () -> new RollBatch("empty", Collections.emptyList()));
        RollResult first = batch("a", 1, "d1", RollMode.NORMAL).results.get(0);
        RollResult different = batch("b", 1, "2d1", RollMode.NORMAL).results.get(0);
        assertThrows(IllegalArgumentException.class, () -> new RollBatch("mixed", Arrays.asList(first, different)));
        assertThrows(IllegalArgumentException.class, () -> new RollBatch("duplicate", Arrays.asList(first, first)));
        RollResult large = batch("large", 1, "1000d1", RollMode.NORMAL).results.get(0);
        List<RollResult> tooLarge = new ArrayList<>();
        for (int i = 0; i < 11; i++) tooLarge.add(new RollResult("r" + i, large.name, large.notation, large.mode, large.firstRoll, null, large.modifier, large.timestamp));
        assertThrows(IllegalArgumentException.class, () -> new RollBatch("work", tooLarge));
    }
    @Test public void retainsNewestWholeBatchesAndDeduplicatesByBatchId() {
        List<RollBatch> previous = new ArrayList<>();
        for (int i = 0; i < RollLimits.MAX_BATCHES; i++) previous.add(batch("old-" + i, 2, "d1", RollMode.NORMAL));
        RollBatch newest = batch("new", 3, "d1", RollMode.NORMAL);
        List<RollBatch> merged = RollBatch.mergeBatches(Arrays.asList(newest, newest), previous);
        assertEquals(100, merged.size()); assertSame(newest, merged.get(0)); assertEquals("old-98", merged.get(99).id);
        assertEquals(3, merged.get(0).count); assertEquals(2, merged.get(99).count);
    }
    @Test public void resultLimitEvictsWholeOldestBatchInsteadOfTrimmingOrSkippingIt() {
        List<RollBatch> all = new ArrayList<>();
        for (int i = 0; i < 9; i++) all.add(batch("full-" + i, 100, "d1", RollMode.NORMAL));
        RollBatch partial = batch("ninety", 90, "d1", RollMode.NORMAL);
        all.add(partial); all.add(batch("twenty", 20, "d1", RollMode.NORMAL)); all.add(batch("older", 1, "d1", RollMode.NORMAL));
        List<RollBatch> retained = RollBatch.keepCompleteBatches(all);
        assertEquals(10, retained.size()); assertEquals(990, retained.stream().mapToInt(value -> value.count).sum()); assertSame(partial, retained.get(9));
    }
    @Test public void physicalDiceLimitCountsSecondGroupsAndEvictsWholeBatches() {
        List<RollBatch> full = new ArrayList<>();
        for (int i = 0; i < 20; i++) full.add(batch("double-" + i, 5, "1000d1", RollMode.ADVANTAGE));
        List<RollBatch> all = new ArrayList<>(full); all.add(batch("extra", 1, "d1", RollMode.NORMAL));
        List<RollBatch> retained = RollBatch.keepCompleteBatches(all);
        assertEquals(full, retained); assertEquals(200000, retained.stream().mapToInt(value -> value.individualDiceCount).sum());
    }
    @Test public void copyIncludesEveryDieBeyondVisiblePage() {
        List<Integer> values = IntStream.rangeClosed(1, 137).boxed().collect(Collectors.toList());
        String text = new ResultTextFormatter(Enum::name, Locale.US).formatResult(result("r", values, null, -2, RollMode.NORMAL, ""));
        String group = text.lines().filter(line -> line.startsWith("FIRST_GROUP:")).findFirst().get();
        List<Integer> copied = Arrays.stream(group.substring(group.indexOf('[') + 1, group.indexOf(']')).split(", ")).map(Integer::valueOf).collect(Collectors.toList());
        assertEquals(values, copied); assertTrue(text.contains("MODIFIER: -2")); assertTrue(text.contains("RESULT: 9,451")); assertFalse(text.contains("SECOND_GROUP"));
    }
    @Test public void copiedComparisonIncludesBothGroupsChosenTotalAndOneModifier() {
        String text = new ResultTextFormatter(Enum::name, Locale.US).formatResult(result("r", Arrays.asList(1, 6), Arrays.asList(4, 4), 3, RollMode.ADVANTAGE, "攻击"));
        for (String expected : new String[]{"NOTE: 攻击", "EXPRESSION: 2d1000+3", "MODE: HIGHER", "FIRST_GROUP: [1, 6] = 7", "SECOND_GROUP: [4, 4] = 8", "CHOSEN_TOTAL: 8", "MODIFIER: +3"}) assertTrue(text, text.contains(expected));
        assertTrue(text.endsWith("RESULT: 11"));
    }
    @Test public void copiedBatchContainsSummaryAndEveryResultInOriginalOrder() {
        RollBatch batch = new RollBatch("batch", Arrays.asList(result("first", Collections.singletonList(2), null, 0, RollMode.NORMAL, ""), result("second", Collections.singletonList(5), null, 0, RollMode.NORMAL, "")));
        String text = new ResultTextFormatter(Enum::name, Locale.US).formatBatch(batch);
        for (String expected : new String[]{"COUNT: 2", "TOTAL: 7", "AVERAGE: 3.5", "MINIMUM: 2", "MAXIMUM: 5"}) assertTrue(text.contains(expected));
        assertEquals(Arrays.asList("RESULT: 2", "RESULT: 5"), text.lines().filter(line -> line.startsWith("RESULT:")).collect(Collectors.toList()));
        assertTrue(text.indexOf("ROLL 1") < text.indexOf("ROLL 2"));
    }
    @Test public void copiedLargeResultsUseLongArithmeticAndLocalizedLabels() {
        String text = new ResultTextFormatter(label -> "中文_" + label.name(), Locale.CHINA).formatResult(result("r", Collections.singletonList(1), null, Integer.MAX_VALUE, RollMode.NORMAL, ""));
        assertTrue(text.contains("中文_RESULT: 2,147,483,648")); assertTrue(text.contains("中文_MODE: 中文_NORMAL")); assertFalse(text.contains("NOTE:"));
    }
    @Test public void modelListsAreDefensivelyCopiedAndImmutable() {
        List<Integer> values = new ArrayList<>(Arrays.asList(1, 2));
        DiceRoll roll = new DiceRoll(values); values.set(0, 6);
        assertEquals(Arrays.asList(1, 2), roll.values); assertEquals(3L, roll.sum);
        assertThrows(UnsupportedOperationException.class, () -> roll.values.add(4));
        List<RollResult> results = new ArrayList<>(batch("b", 2, "d1", RollMode.NORMAL).results);
        RollBatch batch = new RollBatch("b", results); results.clear(); assertEquals(2, batch.count);
        assertThrows(UnsupportedOperationException.class, () -> batch.results.clear());
        List<RollBatch> history = new ArrayList<>(Collections.singletonList(batch));
        HistoryLoadResult load = new HistoryLoadResult(history); history.clear(); assertEquals(1, load.batches.size());
        assertThrows(UnsupportedOperationException.class, () -> load.batches.clear());
    }
}
