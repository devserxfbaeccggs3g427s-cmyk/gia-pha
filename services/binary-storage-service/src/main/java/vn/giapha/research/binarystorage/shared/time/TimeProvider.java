package vn.giapha.research.binarystorage.shared.time;

import java.time.Clock;
import java.time.Instant;

@FunctionalInterface
public interface TimeProvider {

    Instant now();

    static TimeProvider system() {
        return Clock.systemUTC()::instant;
    }

    static TimeProvider fixed(Instant instant) {
        return () -> instant;
    }
}
