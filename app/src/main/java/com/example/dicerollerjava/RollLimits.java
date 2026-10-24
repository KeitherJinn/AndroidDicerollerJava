package com.example.dicerollerjava;

public final class RollLimits {
    private RollLimits() {}
    public static final int MAX_DICE = 1_000, MAX_SIDES = 1_000_000, MAX_REPEATS = 100,
            MAX_TOTAL_DICE = 10_000, MAX_HISTORY = 100, MAX_BATCHES = 100,
            MAX_HISTORY_RESULTS = 1_000, MAX_HISTORY_DICE = 200_000,
            MAX_NOTATION_LENGTH = 128, MAX_NAME_LENGTH = 80, DISPLAYED_VALUES = 50;
}
