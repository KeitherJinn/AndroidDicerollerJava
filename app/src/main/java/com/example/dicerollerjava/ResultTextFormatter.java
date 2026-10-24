package com.example.dicerollerjava;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.Locale;
import java.util.function.Function;

/** Localized plain text export includes every die, independent of UI pagination. */
public final class ResultTextFormatter {
    private final Function<ResultTextLabel, String> label;
    private final Locale locale;
    public ResultTextFormatter(Function<ResultTextLabel, String> label) { this(label, Locale.getDefault()); }
    public ResultTextFormatter(Function<ResultTextLabel, String> label, Locale locale) { this.label = label; this.locale = locale; }
    public String formatBatch(RollBatch batch) {
        // NumberFormat is mutable: reuse it within one export, never across callers.
        NumberFormat integers = NumberFormat.getIntegerInstance(locale);
        NumberFormat averages = NumberFormat.getNumberInstance(locale);
        averages.setMaximumFractionDigits(2);
        StringBuilder text = new StringBuilder(label.apply(ResultTextLabel.BATCH)).append('\n')
                .append(field(ResultTextLabel.COUNT, integers.format(batch.count))).append('\n')
                .append(field(ResultTextLabel.TOTAL, integers.format(batch.total))).append('\n')
                .append(field(ResultTextLabel.AVERAGE, averages.format(batch.average))).append('\n')
                .append(field(ResultTextLabel.MINIMUM, integers.format(batch.minimum))).append('\n')
                .append(field(ResultTextLabel.MAXIMUM, integers.format(batch.maximum)));
        for (int i = 0; i < batch.results.size(); i++) text.append("\n\n").append(label.apply(ResultTextLabel.ROLL))
                .append(' ').append(integers.format(i + 1)).append('\n').append(formatResult(batch.results.get(i), integers));
        return text.toString();
    }
    public String formatResult(RollResult result) {
        return formatResult(result, NumberFormat.getIntegerInstance(locale));
    }
    private String formatResult(RollResult result, NumberFormat integers) {
        StringBuilder text = new StringBuilder();
        if (!DiceEngine.trimWhitespace(result.name).isEmpty()) text.append(field(ResultTextLabel.NOTE, result.name)).append('\n');
        text.append(field(ResultTextLabel.EXPRESSION, result.notation)).append('\n')
                .append(field(ResultTextLabel.MODE, label.apply(result.mode == RollMode.NORMAL ? ResultTextLabel.NORMAL :
                        result.mode == RollMode.ADVANTAGE ? ResultTextLabel.HIGHER : ResultTextLabel.LOWER))).append('\n')
                .append(field(ResultTextLabel.TIME, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM, locale)
                        .format(new Date(result.timestamp)))).append('\n')
                .append(group(ResultTextLabel.FIRST_GROUP, result.firstRoll, integers)).append('\n');
        if (result.secondRoll != null) text.append(group(ResultTextLabel.SECOND_GROUP, result.secondRoll, integers)).append('\n');
        return text.append(field(ResultTextLabel.CHOSEN_TOTAL, integers.format(result.chosenRoll.sum))).append('\n')
                .append(field(ResultTextLabel.MODIFIER, (result.modifier >= 0 ? "+" : "") + integers.format(result.modifier))).append('\n')
                .append(field(ResultTextLabel.RESULT, integers.format(result.finalResult))).toString();
    }
    private String group(ResultTextLabel key, DiceRoll roll, NumberFormat integers) {
        StringBuilder text = new StringBuilder(label.apply(key)).append(": [");
        for (int i = 0; i < roll.values.size(); i++) {
            if (i != 0) text.append(", ");
            text.append(integers.format(roll.values.get(i)));
        }
        return text.append("] = ").append(integers.format(roll.sum)).toString();
    }
    private String field(ResultTextLabel key, String value) { return label.apply(key) + ": " + value; }
}
