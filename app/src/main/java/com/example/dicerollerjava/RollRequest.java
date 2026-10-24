package com.example.dicerollerjava;

import java.util.Objects;

public final class RollRequest {
    public final String name;
    public final DiceExpression dice;
    public final int times;
    public final RollMode mode;
    public RollRequest(String name, DiceExpression dice, int times, RollMode mode) {
        this.name = Objects.requireNonNull(name);
        this.dice = Objects.requireNonNull(dice);
        this.times = times;
        this.mode = Objects.requireNonNull(mode);
    }
    @Override public boolean equals(Object other) {
        if (!(other instanceof RollRequest)) return false;
        RollRequest value = (RollRequest) other;
        return name.equals(value.name) && dice.equals(value.dice) && times == value.times && mode == value.mode;
    }
    @Override public int hashCode() { return Objects.hash(name, dice, times, mode); }
}
