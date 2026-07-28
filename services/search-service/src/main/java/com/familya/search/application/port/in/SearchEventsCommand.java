package com.familya.search.application.port.in;

import java.time.LocalDate;

public record SearchEventsCommand() {
    public record EventFilter(String kind, LocalDate from, LocalDate to, Boolean tombstoned) { }
}
