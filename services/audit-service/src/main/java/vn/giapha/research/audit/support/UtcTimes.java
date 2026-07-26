package vn.giapha.research.audit.support;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public final class UtcTimes {
    private UtcTimes() {
    }

    public static LocalDateTime toDb(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public static Instant fromDb(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toInstant(ZoneOffset.UTC);
    }
}
