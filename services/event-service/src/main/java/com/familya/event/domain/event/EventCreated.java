package com.familya.event.domain.event;

import com.familya.event.domain.model.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Sự kiện thay đổi miền phát sinh khi một {@link DomainEvent sự kiện
 * gia phả} mới được tạo.
 *
 * <p>Việc tạo ra {@code EventCreated} được thực hiện bởi
 * {@link com.familya.event.application.usecase.CreateDomainEventUseCase}
 * (khi tạo mới) và {@link com.familya.event.application.usecase.UpdateDomainEventUseCase}
 * (khi cập nhật lần đầu hoặc khi policy yêu cầu phát lại) thông qua
 * adapter {@link com.familya.event.adapter.out.events.OutboxEventChangePublisher}.
 *
 * <p>Đây là sự kiện <b>change</b>: chỉ phát ra khi có thay đổi trạng thái,
 * giúp các projection tiêu thụ phân biệt với sự kiện <i>fact</i> dùng
 * cho tái dựng.
 *
 * <p>Schema phiên bản {@code 1}.
 *
 * @author gia-pha platform
 */
public final class EventCreated extends DomainEventChange {

    /** Định danh duy nhất của sự kiện gia phả vừa được tạo/cập nhật. */
    private final UUID eventId;

    /** Định danh cây gia phả liên quan — cũng là partition key. */
    private final UUID treeId;

    /** Phân loại sự kiện gia phả (BIRTH, MARRIAGE, ...). */
    private final DomainEvent.Kind kind;

    /** Tiêu đề sự kiện, dùng cho projection hiển thị nhanh. */
    private final String title;

    /** Revision của aggregate tại thời điểm phát sinh. */
    private final long revision;

    /** Thời điểm phát sinh (UTC). */
    private final Instant occurredAt;

    /**
     * Khởi tạo sự kiện {@code EventCreated} với đầy đủ thông tin bắt buộc.
     *
     * @param eventId    định danh duy nhất của sự kiện gia phả.
     * @param treeId     định danh cây gia phả.
     * @param kind       loại sự kiện.
     * @param title      tiêu đề hiện tại (sau cập nhật nếu có).
     * @param revision   revision ngữ nghĩa của aggregate.
     * @param occurredAt thời điểm phát sinh (UTC).
     */
    public EventCreated(UUID eventId, UUID treeId, DomainEvent.Kind kind, String title,
                        long revision, Instant occurredAt) {
        this.eventId = eventId;
        this.treeId = treeId;
        this.kind = kind;
        this.title = title;
        this.revision = revision;
        this.occurredAt = occurredAt;
    }

    /** {@inheritDoc} */
    @Override public UUID treeId() { return treeId; }

    /** {@inheritDoc} */
    @Override public UUID eventId() { return eventId; }

    /** {@inheritDoc} */
    @Override public String eventType() { return "EventCreated"; }

    /** {@inheritDoc} */
    @Override public int eventVersion() { return 1; }

    /** {@inheritDoc} */
    @Override public Instant occurredAt() { return occurredAt; }

    /** {@inheritDoc} */
    @Override public long revision() { return revision; }

    /** @return loại sự kiện đã tạo. */
    public DomainEvent.Kind kind() { return kind; }

    /** @return tiêu đề của sự kiện. */
    public String title() { return title; }
}
