package vn.giapha.research.tree.infrastructure.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import vn.giapha.research.tree.infrastructure.kernel.time.UtcTimes;

public final class JdbcSupport {
    private JdbcSupport() {}

    public static long requiredKey(GeneratedKeyHolder keyHolder) {
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("MySQL did not return a generated key");
        }
        return key.longValue();
    }

    public static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return UtcTimes.fromDb(resultSet.getObject(column, LocalDateTime.class));
    }
}
