package com.example.dicerollerjava;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class DiceRoll {
    public final List<Integer> values;
    public final long sum;
    public DiceRoll(List<Integer> values) {
        this.values = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(values)));
        long total = 0;
        for (int value : this.values) total += value;
        sum = total;
    }
    @Override public boolean equals(Object other) {
        return other instanceof DiceRoll && values.equals(((DiceRoll) other).values);
    }
    @Override public int hashCode() { return values.hashCode(); }
}
