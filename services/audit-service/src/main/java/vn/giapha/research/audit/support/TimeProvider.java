package vn.giapha.research.audit.support;

import java.time.Clock;
import java.time.Instant;

@FunctionalInterface
public interface TimeProvider {
    Instant now();

    static TimeProvider system() {
        return Clock.systemUTC()::instant;
    }
}
