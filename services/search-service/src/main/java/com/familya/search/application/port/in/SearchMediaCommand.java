package com.familya.search.application.port.in;

public record SearchMediaCommand() {
    public record MediaFilter(String kind, Boolean tombstoned) { }
}
