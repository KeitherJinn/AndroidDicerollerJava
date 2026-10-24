package com.example.dicerollerjava;

import java.util.Objects;

public final class DiceExpression {
    public final int numberOfDice;
    public final int sides;
    public final long modifier;
    public final String notation;
    public DiceExpression(int numberOfDice, int sides, long modifier) {
        this.numberOfDice = numberOfDice;
        this.sides = sides;
        this.modifier = modifier;
        this.notation = numberOfDice + "d" + sides + (modifier > 0 ? "+" + modifier : modifier < 0 ? Long.toString(modifier) : "");
    }
    @Override public boolean equals(Object other) {
        if (!(other instanceof DiceExpression)) return false;
        DiceExpression value = (DiceExpression) other;
        return numberOfDice == value.numberOfDice && sides == value.sides && modifier == value.modifier;
    }
    @Override public int hashCode() { return Objects.hash(numberOfDice, sides, modifier); }
    @Override public String toString() { return notation; }
}
