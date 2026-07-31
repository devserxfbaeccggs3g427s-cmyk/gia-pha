package com.familya.platform.inbox;

import java.time.Instant;

/**
 * Bản ghi inbox đại diện cho một sự kiện đã được consumer xử lý.
 *
 * <p>Consumer thực hiện dedupe theo {@code eventId}. Các bản ghi vượt quá
 * {@code retention_days} sẽ được xoá bởi một job định kỳ. Inbox là ranh giới
 * an toàn cho cơ chế at-least-once — các handler downstream vẫn phải idempotent
 * theo aggregate version.</p>
 *
 * <p>Đây là {@code record} bất biến, đảm bảo dữ liệu không bị thay đổi sau
 * khi tạo và an toàn khi chia sẻ giữa các luồng.</p>
 *
 * @param eventId     định danh duy nhất của sự kiện (UUID)
 * @param consumer    tên consumer xử lý sự kiện
 * @param topic       tên Kafka topic mà sự kiện đến từ
 * @param partition   số partition trong topic
 * @param offset      offset của message trong partition
 * @param consumedAt  thời điểm consumer xử lý xong sự kiện
 *
 * @author Family Tree Platform Team
 */
public record InboxRecord(
        String eventId,
        String consumer,
        String topic,
        int partition,
        long offset,
        Instant consumedAt
) { }
