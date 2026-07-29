package com.familya.media.domain.model;

public record ScannerResult(Outcome outcome, String evidence) {
    public enum Outcome { CLEAN, INFECTED, FAILED }
}
