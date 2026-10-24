package com.example.dicerollerjava;

import java.util.Objects;

public final class RollResult {
    public final String id, name, notation;
    public final RollMode mode;
    public final DiceRoll firstRoll, secondRoll, chosenRoll;
    public final long modifier, timestamp, finalResult;
    public RollResult(String id, String name, String notation, RollMode mode, DiceRoll firstRoll,
                      DiceRoll secondRoll, long modifier, long timestamp) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.notation = Objects.requireNonNull(notation);
        this.mode = Objects.requireNonNull(mode);
        this.firstRoll = Objects.requireNonNull(firstRoll);
        this.secondRoll = secondRoll;
        this.modifier = modifier;
        this.timestamp = timestamp;
        switch (mode) {
            case ADVANTAGE: chosenRoll = firstRoll.sum >= Objects.requireNonNull(secondRoll).sum ? firstRoll : secondRoll; break;
            case DISADVANTAGE: chosenRoll = firstRoll.sum <= Objects.requireNonNull(secondRoll).sum ? firstRoll : secondRoll; break;
            default: chosenRoll = firstRoll;
        }
        finalResult = chosenRoll.sum + modifier;
    }
    @Override public boolean equals(Object other) {
        if (!(other instanceof RollResult)) return false;
        RollResult value = (RollResult) other;
        return id.equals(value.id) && name.equals(value.name) && notation.equals(value.notation)
                && mode == value.mode && firstRoll.equals(value.firstRoll)
                && Objects.equals(secondRoll, value.secondRoll) && modifier == value.modifier && timestamp == value.timestamp;
    }
    @Override public int hashCode() { return Objects.hash(id, name, notation, mode, firstRoll, secondRoll, modifier, timestamp); }
}
