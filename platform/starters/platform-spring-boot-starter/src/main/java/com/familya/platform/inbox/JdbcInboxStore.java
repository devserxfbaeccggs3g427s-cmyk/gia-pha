package com.familya.platform.inbox;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Default JDBC inbox store. Schema is created by the service's
 * Flyway migration:
 *
 * <pre>
 * CREATE TABLE inbox_record (
 *   event_id    CHAR(36)  NOT NULL,
 *   consumer    VARCHAR(64) NOT NULL,
 *   topic       VARCHAR(128) NOT NULL,
 *   partition_no INT NOT NULL,
 *   offset_no   BIGINT NOT NULL,
 *   consumed_at TIMESTAMP(6) NOT NULL,
 *   PRIMARY KEY (event_id, consumer)
 * );
 * </pre>
 */
@Component
public class JdbcInboxStore implements InboxStore {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcInboxStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(String eventId, String consumer) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(1) FROM inbox_record WHERE event_id = :e AND consumer = :c",
                new MapSqlParameterSource().addValue("e", eventId).addValue("c", consumer),
                Integer.class);
        return n != null && n > 0;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void markProcessed(InboxRecord r) {
        jdbc.update(
                "INSERT IGNORE INTO inbox_record (event_id, consumer, topic, partition_no, offset_no, consumed_at) "
                        + "VALUES (:e, :c, :t, :p, :o, :ts)",
                new MapSqlParameterSource()
                        .addValue("e", r.eventId())
                        .addValue("c", r.consumer())
                        .addValue("t", r.topic())
                        .addValue("p", r.partition())
                        .addValue("o", r.offset())
                        .addValue("ts", Timestamp.from(r.consumedAt())));
    }
}
