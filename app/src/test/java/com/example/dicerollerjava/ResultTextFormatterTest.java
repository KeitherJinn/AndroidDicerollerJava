package com.example.dicerollerjava;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ResultTextFormatterTest {
    private RollBatch localizedBatch() {
        return new RollBatch("localized", Arrays.asList(
                new RollResult("one", "Attack", "2d1000000-3", RollMode.ADVANTAGE,
                        new DiceRoll(Arrays.asList(1000, 2)), new DiceRoll(Arrays.asList(4, 2000)), -3, 0),
                new RollResult("two", "Attack", "2d1000000-3", RollMode.ADVANTAGE,
                        new DiceRoll(Arrays.asList(1, 1002)), new DiceRoll(Arrays.asList(1000, 5)), -3, 1)));
    }

    @Test public void preservesLocalizedNumbersInBatchSummaryAndEveryResult() {
        // Fixed expected values, including Arabic bidi sign and decimal/group separators.
        String[][] cases = {
                {"en-US", "2", "3,003", "1,501.5", "1,002", "2,001", "[1,000, 2] = 1,002", "[4, 2,000] = 2,004", "2,004", "-3"},
                {"zh-CN", "2", "3,003", "1,501.5", "1,002", "2,001", "[1,000, 2] = 1,002", "[4, 2,000] = 2,004", "2,004", "-3"},
                {"de-DE", "2", "3.003", "1.501,5", "1.002", "2.001", "[1.000, 2] = 1.002", "[4, 2.000] = 2.004", "2.004", "-3"},
                {"ar-EG", "٢", "٣٬٠٠٣", "١٬٥٠١٫٥", "١٬٠٠٢", "٢٬٠٠١", "[١٬٠٠٠, ٢] = ١٬٠٠٢", "[٤, ٢٬٠٠٠] = ٢٬٠٠٤", "٢٬٠٠٤", "\u061c-٣"}
        };
        RollBatch batch = localizedBatch();
        for (String[] expected : cases) {
            ResultTextFormatter formatter = new ResultTextFormatter(Enum::name, Locale.forLanguageTag(expected[0]));
            String text = formatter.formatBatch(batch);
            assertTrue(expected[0], text.startsWith("BATCH\nCOUNT: " + expected[1] + "\nTOTAL: " + expected[2]
                    + "\nAVERAGE: " + expected[3] + "\nMINIMUM: " + expected[4] + "\nMAXIMUM: " + expected[5] + "\n\n"));
            String firstResult = formatter.formatResult(batch.results.get(0));
            assertTrue(expected[0], firstResult.contains("FIRST_GROUP: " + expected[6] + "\nSECOND_GROUP: " + expected[7]
                    + "\nCHOSEN_TOTAL: " + expected[8] + "\nMODIFIER: " + expected[9] + "\nRESULT: " + expected[5]));
            assertTrue(expected[0], text.contains(firstResult));
            assertTrue(expected[0], text.endsWith(formatter.formatResult(batch.results.get(1))));
            assertEquals(expected[0], 2, text.lines().filter(line -> line.startsWith("RESULT:")).count());
        }
    }

    @Test public void maximumWorkBatchExportsAllTenThousandDiceInOrder() {
        List<Integer> values = new ArrayList<>();
        for (int value = 1; value <= 1000; value++) values.add(value);
        List<RollResult> results = new ArrayList<>();
        for (int index = 0; index < 10; index++) results.add(new RollResult("r" + index, "", "1000d1000",
                RollMode.NORMAL, new DiceRoll(values), null, 0, index));
        RollBatch batch = new RollBatch("maximum-work", results);
        String text = new ResultTextFormatter(Enum::name, Locale.US).formatBatch(batch);
        assertEquals(10000, batch.individualDiceCount);
        assertEquals(10, text.lines().filter(line -> line.startsWith("FIRST_GROUP:")).count());
        String expectedGroup = "FIRST_GROUP: [" + String.join(", ",
                values.stream().map(value -> value == 1000 ? "1,000" : value.toString()).toArray(String[]::new)) + "] = 500,500";
        assertEquals(10, text.lines().filter(expectedGroup::equals).count());
        assertTrue(text.contains("TOTAL: 5,005,000\nAVERAGE: 500,500\n"));
        assertTrue(text.endsWith("RESULT: 500,500"));
    }

    @Test public void sharedFormatterKeepsConcurrentExportsIndependent() throws Exception {
        RollBatch batch = localizedBatch();
        ResultTextFormatter formatter = new ResultTextFormatter(Enum::name, Locale.forLanguageTag("ar-EG"));
        String expectedBatch = formatter.formatBatch(batch);
        String expectedResult = formatter.formatResult(batch.results.get(0));
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            Callable<Void> job = () -> {
                for (int index = 0; index < 100; index++) {
                    assertEquals(expectedBatch, formatter.formatBatch(batch));
                    assertEquals(expectedResult, formatter.formatResult(batch.results.get(0)));
                }
                return null;
            };
            List<Future<Void>> futures = executor.invokeAll(Collections.nCopies(8, job));
            for (Future<Void> future : futures) future.get();
        } finally {
            executor.shutdownNow();
        }
    }
}
