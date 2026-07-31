package com.familya.event.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Sự kiện thay đổi miền phát sinh khi một
 * {@link com.familya.event.domain.model.DomainEvent sự kiện gia phả}
 * bị tombstone (xóa mềm).
 *
 * <p>Sự kiện này được phát ra bởi
 * {@link com.familya.event.application.usecase.TombstoneDomainEventUseCase}
 * thông qua {@link com.familya.event.adapter.out.events.OutboxEventChangePublisher}.
 *
 * <p>Vai trò:
 * <ul>
 *   <li>Báo hiệu cho projection (read-model) cập nhật trạng thái
 *       hiển thị — ví dụ ẩn sự kiện khỏi danh sách mặc định.</li>
 *   <li>Báo hiệu cho search index gỡ sự kiện khỏi kết quả tìm kiếm.</li>
 *   <li>Cho phép các consumer khác (media, audit) phản ứng phù hợp.</li>
 * </ul>
 *
 * <p>Lưu ý: tombstone về bản chất là xóa mềm; aggregate vẫn tồn tại
 * trong cơ sở dữ liệu để phục vụ khôi phục hoặc audit. Schema phiên bản
 * {@code 1}.
 *
 * @author gia-pha platform
 */
public final class EventTombstoned extends DomainEventChange {

    /** Định danh duy nhất của sự kiện gia phả vừa bị tombstone. */
    private final UUID eventId;

    /** Định danh cây gia phả — cũng là partition key. */
    private final UUID treeId;

    /** Revision của aggregate tại thời điểm tombstone. */
    private final long revision;

    /** Thời điểm phát sinh tombstone (UTC). */
    private final Instant occurredAt;

    /**
     * Khởi tạo sự kiện {@code EventTombstoned}.
     *
     * @param eventId    định danh duy nhất của sự kiện gia phả.
     * @param treeId     định danh cây gia phả.
     * @param revision   revision ngữ nghĩa sau khi tombstone.
     * @param occurredAt thời điểm phát sinh (UTC).
     */
    public EventTombstoned(UUID eventId, UUID treeId, long revision, Instant occurredAt) {
        this.eventId = eventId;
        this.treeId = treeId;
        this.revision = revision;
        this.occurredAt = occurredAt;
    }

    /** {@inheritDoc} */
    @Override public UUID treeId() { return treeId; }

    /** {@inheritDoc} */
    @Override public UUID eventId() { return eventId; }

    /** {@inheritDoc} */
    @Override public String eventType() { return "EventTombstoned"; }

    /** {@inheritDoc} */
    @Override public int eventVersion() { return 1; }

    /** {@inheritDoc} */
    @Override public Instant occurredAt() { return occurredAt; }

    /** {@inheritDoc} */
    @Override public long revision() { return revision; }
}
