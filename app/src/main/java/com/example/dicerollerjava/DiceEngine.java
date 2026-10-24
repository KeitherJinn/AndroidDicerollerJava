package com.example.dicerollerjava;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded dice parsing and calculation, independent of Android UI and storage. */
public class DiceEngine {
    private static final Pattern NOTATION = Pattern.compile("^\\s*(\\d*)[dD](\\d+)\\s*([+-]\\s*\\d+)?\\s*$");
    private final Random random;
    private final LongSupplier now;
    private final Supplier<String> newId;
    public DiceEngine() { this(new Random()); }
    public DiceEngine(Random random) { this(random, System::currentTimeMillis, () -> UUID.randomUUID().toString()); }
    public DiceEngine(Random random, LongSupplier now, Supplier<String> newId) {
        this.random = Objects.requireNonNull(random);
        this.now = Objects.requireNonNull(now);
        this.newId = Objects.requireNonNull(newId);
    }
    public static ParsedDice parseDiceInput(String notation) {
        Objects.requireNonNull(notation);
        if (notation.length() > RollLimits.MAX_NOTATION_LENGTH) return new ParsedDice(null, InputIssue.NOTATION_LENGTH);
        Matcher match = NOTATION.matcher(Normalizer.normalize(notation, Normalizer.Form.NFKC));
        if (!match.matches()) return new ParsedDice(null, InputIssue.NOTATION_FORMAT);
        // Keep both branches boxed: an overflowing input must remain null until validation.
        Integer count = match.group(1).isEmpty() ? Integer.valueOf(1) : integerOrNull(match.group(1));
        if (count == null || count < 1 || count > RollLimits.MAX_DICE) return new ParsedDice(null, InputIssue.DICE_LIMIT);
        Integer sides = integerOrNull(match.group(2));
        if (sides == null || sides < 1 || sides > RollLimits.MAX_SIDES) return new ParsedDice(null, InputIssue.SIDES_LIMIT);
        String modifierText = match.group(3);
        Long modifier = modifierText == null ? Long.valueOf(0L) : longOrNull(modifierText.replaceAll("\\s", ""));
        if (modifier == null || modifier < Integer.MIN_VALUE || modifier > Integer.MAX_VALUE) return new ParsedDice(null, InputIssue.MODIFIER_LIMIT);
        return new ParsedDice(new DiceExpression(count, sides, modifier));
    }
    public static DiceExpression parseDiceNotation(String notation) { return parseDiceInput(notation).dice; }
    public static RequestValidation validateRequest(String name, String notation, String timesText, RollMode mode) {
        Objects.requireNonNull(mode);
        ParsedDice parsed = parseDiceInput(notation);
        Integer times = integerOrNull(Normalizer.normalize(trimWhitespace(timesText), Normalizer.Form.NFKC));
        InputIssue timesIssue = null;
        if (times == null || times < 1 || times > RollLimits.MAX_REPEATS) timesIssue = InputIssue.REPEAT_LIMIT;
        else if (parsed.dice != null && (long) parsed.dice.numberOfDice * times *
                (mode == RollMode.NORMAL ? 1 : 2) > RollLimits.MAX_TOTAL_DICE) timesIssue = InputIssue.WORK_LIMIT;
        if (parsed.dice == null || timesIssue != null) return new RequestValidation(null, parsed.issue, timesIssue);
        String trimmed = trimWhitespace(name);
        return new RequestValidation(new RollRequest(trimmed.substring(0, Math.min(trimmed.length(), RollLimits.MAX_NAME_LENGTH)), parsed.dice, times, mode));
    }
    static String trimWhitespace(String input) {
        int start = 0, end = input.length();
        while (start < end && isWhitespace(input.charAt(start))) start++;
        while (end > start && isWhitespace(input.charAt(end - 1))) end--;
        return input.substring(start, end);
    }
    private static boolean isWhitespace(char value) { return Character.isWhitespace(value) || Character.isSpaceChar(value); }
    private static Integer integerOrNull(String value) {
        try { return Integer.valueOf(value); } catch (NumberFormatException error) { return null; }
    }
    private static Long longOrNull(String value) {
        try { return Long.valueOf(value); } catch (NumberFormatException error) { return null; }
    }
    public RollResult roll(RollRequest request) { return roll(request, () -> {}); }
    public RollResult roll(RollRequest request, Runnable checkpoint) {
        DiceExpression dice = request.dice;
        if (dice.numberOfDice < 1 || dice.numberOfDice > RollLimits.MAX_DICE ||
                dice.sides < 1 || dice.sides > RollLimits.MAX_SIDES ||
                dice.modifier < Integer.MIN_VALUE || dice.modifier > Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid dice limits.");
        DiceRoll first = rollGroup(dice, checkpoint);
        DiceRoll second = request.mode == RollMode.NORMAL ? null : rollGroup(dice, checkpoint);
        return new RollResult(newId.get(), request.name, dice.notation, request.mode, first, second, dice.modifier, now.getAsLong());
    }
    private DiceRoll rollGroup(DiceExpression dice, Runnable checkpoint) {
        List<Integer> values = new ArrayList<>(dice.numberOfDice);
        for (int index = 0; index < dice.numberOfDice; index++) {
            if (index % 64 == 0) checkpoint.run();
            values.add(random.nextInt(dice.sides) + 1);
        }
        return new DiceRoll(values);
    }
    public static List<RollResult> mergeHistory(List<RollResult> newestFirst, List<RollResult> previous) {
        List<RollResult> combined = new ArrayList<>(newestFirst);
        combined.addAll(previous);
        List<RollResult> retained = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (RollResult result : combined) {
            if (ids.add(result.id)) retained.add(result);
            if (retained.size() == RollLimits.MAX_HISTORY) break;
        }
        return Collections.unmodifiableList(retained);
    }
}
