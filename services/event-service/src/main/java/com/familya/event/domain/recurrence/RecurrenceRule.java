package com.familya.event.domain.model;

import java.time.LocalDate;
import java.util.*;

/**
 * Recurrence rule (RFC 5545 v1 subset). Supports DAILY, WEEKLY,
 * MONTHLY, YEARLY with COUNT or UNTIL termination. February-29
 * birthdays follow the legacy behaviour: anchor in non-leap years is
 * February 28.
 */
public record RecurrenceRule(Frequency frequency, int interval, Termination termination) {

    public enum Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

    public sealed interface Termination permits Termination.Count, Termination.Until { }

    public record Count(int count) implements Termination {
        public Count { if (count < 1) throw new IllegalArgumentException("count must be >= 1"); }
    }
    public record Until(LocalDate until) implements Termination {
        public Until { Objects.requireNonNull(until); }
    }

    /**
     * Produces the next occurrence after {@code from}. February-29
     * anchors in non-leap years fall back to February 28 (legacy
     * behaviour).
     */
    public LocalDate nextAfter(LocalDate from, LocalDate anchor) {
        Objects.requireNonNull(from);
        Objects.requireNonNull(anchor);
        LocalDate candidate = switch (frequency) {
            case DAILY   -> from.plusDays(interval);
            case WEEKLY  -> from.plusWeeks(interval);
            case MONTHLY -> from.plusMonths(interval);
            case YEARLY  -> from.plusYears(interval);
        };
        candidate = applyLeapDay(anchor, candidate);
        return candidate;
    }

    public boolean isTerminated(LocalDate current) {
        return switch (termination) {
            case Count c -> false; // count-based; caller tracks iteration
            case Until u -> !current.isBefore(u.until());
        };
    }

    public static LocalDate applyLeapDay(LocalDate anchor, LocalDate candidate) {
        if (anchor.getMonthValue() == 2 && anchor.getDayOfMonth() == 29) {
            if (candidate.isLeapYear()) {
                return LocalDate.of(candidate.getYear(), 2, 29);
            }
            return LocalDate.of(candidate.getYear(), 2, 28);
        }
        return candidate;
    }
}