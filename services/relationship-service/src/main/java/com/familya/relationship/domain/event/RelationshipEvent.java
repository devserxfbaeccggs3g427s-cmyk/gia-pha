package com.familya.relationship.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Lớp cơ sở (abstract base) cho tất cả các sự kiện miền (domain event) của
 * Relationship service.
 * <p>
 * Mọi sự kiện được phát hành bởi service này đều thuộc topic
 * {@code relationship.events.v1} và được phân vùng (partition) theo
 * {@link #partitionKey()} - mặc định là {@link #treeId()}. Việc phân vùng theo
 * cây gia phả đảm bảo:
 * </p>
 * <ul>
 *   <li><b>Tính nhất quán thứ tự (ordering):</b> mọi sự kiện trong cùng một cây
 *       được xử lý theo đúng thứ tự phát sinh bởi các consumer.</li>
 *   <li><b>Bộ tuần tự hóa theo cây (per-tree serializer):</b> các lệnh ghi đồng
 *       thời cho cùng một cây phải xếp hàng theo thứ tự, trong khi các cây khác
 *       nhau có thể xử lý song song.</li>
 * </ul>
 *
 * <p>
 * Các lớp con bắt buộc phải cung cấp:
 * {@link #treeId()}, {@link #eventType()}, {@link #eventVersion()},
 * {@link #occurredAt()} và {@link #commandSeq()}. Các trường này là chìa khóa
 * để downstream consumer (Member service, Authorization service, ...) tái dựng
 * được trạng thái nhất quán với Relationship service.
 * </p>
 */
public abstract class RelationshipEvent {

    /**
     * @return định danh cây gia phả mà sự kiện liên quan tới.
     *         Đồng thời là khóa phân vùng (partition key) trên Kafka.
     */
    public abstract UUID treeId();

    /**
     * @return tên loại sự kiện (ví dụ: {@code "RelationshipCreated"}).
     *         Dùng để router trong hệ thống consumer và cho việc deserialize.
     */
    public abstract String eventType();

    /**
     * @return phiên bản schema của sự kiện (hiện tại luôn là {@code 1}).
     *         Tăng lên khi cấu trúc payload thay đổi để hỗ trợ versioning.
     */
    public abstract int eventVersion();

    /**
     * @return thời điểm sự kiện xảy ra tại phía Relationship service.
     */
    public abstract Instant occurredAt();

    /**
     * @return số thứ tự lệnh theo cây (command sequence).
     *         Đây là số gia tăng đơn điệu cho mỗi cây, được tạo bởi
     *         {@code RelationshipRepository.nextCommandSeq} trong transaction.
     */
    public abstract long commandSeq();

    /**
     * @return tên Kafka topic mà sự kiện này được gửi tới.
     *         Mặc định {@code "relationship.events.v1"}.
     */
    public String topic() { return "relationship.events.v1"; }

    /**
     * @return khóa phân vùng Kafka (đảm bảo các sự kiện cùng cây vào cùng partition).
     */
    public String partitionKey() { return treeId().toString(); }
}