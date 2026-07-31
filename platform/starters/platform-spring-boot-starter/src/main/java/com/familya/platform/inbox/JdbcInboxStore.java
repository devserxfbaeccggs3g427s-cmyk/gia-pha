package com.familya.platform.inbox;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Triển khai mặc định của {@link InboxStore} sử dụng JDBC.
 *
 * <p>Bảng {@code inbox_record} được tạo bởi migration Flyway của dịch vụ;
 * schema cố định như sau:</p>
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
 *
 * <p>Khoá chính tổng hợp {@code (event_id, consumer)} cho phép nhiều consumer
 * xử lý cùng một sự kiện mà không xung đột, đồng thời đảm bảo mỗi consumer
 * chỉ xử lý một sự kiện đúng một lần.</p>
 *
 * @author Family Tree Platform Team
 */
@Component
public class JdbcInboxStore implements InboxStore {

    /** Template JDBC dùng để thực thi truy vấn. */
    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Khởi tạo store với {@link NamedParameterJdbcTemplate}.
     *
     * @param jdbc template JDBC
     */
    public JdbcInboxStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Kiểm tra xem một sự kiện đã được consumer xử lý hay chưa.
     *
     * @param eventId  định danh duy nhất của sự kiện
     * @param consumer tên consumer đang xử lý
     * @return {@code true} nếu đã tồn tại bản ghi (đã xử lý), {@code false} nếu chưa
     */
    @Override
    @Transactional(readOnly = true)
    public boolean exists(String eventId, String consumer) {
        // Bước 1: Đếm số bản ghi khớp với (event_id, consumer).
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(1) FROM inbox_record WHERE event_id = :e AND consumer = :c",
                new MapSqlParameterSource().addValue("e", eventId).addValue("c", consumer),
                Integer.class);

        // Bước 2: Trả về true nếu số đếm > 0, false trong trường hợp ngược lại.
        return n != null && n > 0;
    }

    /**
     * Ghi nhận một sự kiện đã được xử lý bởi consumer. Sử dụng {@code INSERT IGNORE}
     * để đảm bảo idempotency: nếu bản ghi đã tồn tại (xử lý lặp) thì câu lệnh
     * không gây lỗi.
     *
     * @param r bản ghi inbox cần ghi nhận
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void markProcessed(InboxRecord r) {
        // Bước 1: Thực thi INSERT IGNORE để chèn bản ghi nếu chưa tồn tại,
        // bỏ qua nếu đã có. Đây là cách an toàn nhất để xử lý lặp.
        jdbc.update(
                "INSERT IGNORE INTO inbox_record (event_id, consumer, topic, partition_no, offset_no, consumed_at) "
                        + "VALUES (:e, :c, :t, :p, :o, :ts)",
                new MapSqlParameterSource()
                        .addValue("e", r.eventId())
                        .addValue("c", r.consumer())
                        .addValue("t", r.topic())
                        .addValue("p", r.partition())
                        .addValue("o", r.offset())
                        // Chuyển Instant sang Timestamp(6) để giữ độ chính xác microsecond.
                        .addValue("ts", Timestamp.from(r.consumedAt())));
    }
}
