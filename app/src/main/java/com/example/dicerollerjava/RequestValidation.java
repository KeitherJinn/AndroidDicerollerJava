package com.example.dicerollerjava;

import java.util.Objects;

public final class RequestValidation {
    public final RollRequest request;
    public final InputIssue notationIssue, timesIssue;
    public RequestValidation(RollRequest request, InputIssue notationIssue, InputIssue timesIssue) {
        this.request = request; this.notationIssue = notationIssue; this.timesIssue = timesIssue;
    }
    public RequestValidation(RollRequest request) { this(request, null, null); }
    @Override public boolean equals(Object other) {
        if (!(other instanceof RequestValidation)) return false;
        RequestValidation value = (RequestValidation) other;
        return Objects.equals(request, value.request) && notationIssue == value.notationIssue && timesIssue == value.timesIssue;
    }
    @Override public int hashCode() { return Objects.hash(request, notationIssue, timesIssue); }
}
