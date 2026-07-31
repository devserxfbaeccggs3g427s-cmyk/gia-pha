package com.familya.event.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Lớp cơ sở (abstract) cho mọi <b>sự kiện thay đổi miền (domain change
 * event)</b> mà <b>event-service</b> phát hành ra ngoài qua outbox.
 *
 * <p>Mỗi sự kiện thuộc loại {@code DomainEventChange} mang đầy đủ thông
 * tin cần thiết để:
 * <ul>
 *   <li>Xác định phân vùng Kafka thông qua {@link #partitionKey()} (luôn
 *       bằng {@link #treeId()}). Việc partition theo cây đảm bảo thứ tự
 *       xử lý trong cùng một cây gia phả.</li>
 *   <li>Giúp consumer xác định phiên bản schema của sự kiện
 *       ({@link #eventVersion()}).</li>
 *   <li>Cho phép consumer replay nhờ {@link #revision()} — revision tương
 *       ứng với phiên bản aggregate tại thời điểm phát sinh.</li>
 *   <li>Ghi nhận thời điểm phát sinh ({@link #occurredAt()}).</li>
 * </ul>
 *
 * <h2>Schema &amp; topic</h2>
 * <ul>
 *   <li>Topic mặc định: {@code event.events.v1} — định nghĩa trong
 *       {@link #topic()}.</li>
 *   <li>Các lớp con cụ thể:
 *       <ul>
 *         <li>{@link EventCreated} — phát sinh khi sự kiện được tạo.</li>
 *         <li>{@link EventTombstoned} — phát sinh khi sự kiện bị xóa mềm.</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <p>Đây là sự kiện dạng <i>change</i> (chỉ phát ra khi có thay đổi),
 * khác với sự kiện dạng <i>fact</i> được dùng cho projection (xem
 * {@code Projector} trong package {@code adapter.in.kafka}).
 *
 * @author gia-pha platform
 * @version 1.0
 */
public abstract class DomainEventChange {

    /**
     * @return định danh cây gia phả mà sự kiện thuộc về.
     */
    public abstract UUID treeId();

    /**
     * @return định danh duy nhất của {@link com.familya.event.domain.model.DomainEvent}
     *         mà sự kiện change này đề cập đến.
     */
    public abstract UUID eventId();

    /**
     * @return tên kiểu sự kiện (ví dụ: {@code "EventCreated"}, {@code "EventTombstoned"}),
     *         dùng để routing ở consumer.
     */
    public abstract String eventType();

    /**
     * @return phiên bản schema của sự kiện. Tăng khi payload thay đổi không
     *         tương thích ngược.
     */
    public abstract int eventVersion();

    /**
     * @return thời điểm phát sinh sự kiện (UTC). Thường dùng làm
     *         timestamp audit/log.
     */
    public abstract Instant occurredAt();

    /**
     * @return revision tương ứng của aggregate tại thời điểm phát sinh.
     *         Consumer có thể dùng để kiểm tra tính liên tục khi stream
     *         sự kiện.
     */
    public abstract long revision();

    /**
     * Tên Kafka topic mà sự kiện này sẽ được publish.
     *
     * @return topic mặc định {@code "event.events.v1"}.
     */
    public String topic() { return "event.events.v1"; }

    /**
     * Khóa phân vùng (partition key) cho Kafka. Theo quy ước, đây là
     * {@link #treeId()} để đảm bảo mọi sự kiện của cùng một cây nằm
     * trên cùng partition và được xử lý có thứ tự.
     *
     * @return chuỗi UUID của cây gia phả.
     */
    public String partitionKey() { return treeId().toString(); }
}
