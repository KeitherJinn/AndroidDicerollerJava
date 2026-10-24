package com.example.dicerollerjava;

import java.util.Objects;

public final class ParsedDice {
    public final DiceExpression dice;
    public final InputIssue issue;
    public ParsedDice(DiceExpression dice, InputIssue issue) { this.dice = dice; this.issue = issue; }
    public ParsedDice(DiceExpression dice) { this(dice, null); }
    @Override public boolean equals(Object other) {
        if (!(other instanceof ParsedDice)) return false;
        ParsedDice value = (ParsedDice) other;
        return Objects.equals(dice, value.dice) && issue == value.issue;
    }
    @Override public int hashCode() { return Objects.hash(dice, issue); }
}
