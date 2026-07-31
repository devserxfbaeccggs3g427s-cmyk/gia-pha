package com.familya.event.application.port.out;

import com.familya.event.domain.event.DomainEventChange;

/**
 * Cổng ra dùng để <b>phát hành</b> các sự kiện thay đổi miền
 * {@link DomainEventChange} ra ngoài hệ thống.
 *
 * <p>Triển khai điển hình là
 * {@link com.familya.event.adapter.out.events.OutboxEventChangePublisher},
 * ghi sự kiện vào bảng outbox để một relay phát lên Kafka (transactional
 * outbox pattern) — đảm bảo sự kiện không bị mất khi DB commit nhưng
 * Kafka publish thất bại.
 *
 * @author gia-pha platform
 */
public interface EventChangePublisher {

    /**
     * Ghi nhận sự kiện vào outbox để phát hành bất đồng bộ.
     *
     * <p>Lưu ý: phương thức này chỉ <i>ghi</i> vào outbox — việc gửi
     * thực sự tới Kafka được thực hiện bởi relay theo chu kỳ (xem
     * {@code familya.outbox.relay.*} trong {@code application.yml}).
     *
     * @param change sự kiện cần phát hành; không được {@code null}.
     */
    void publish(DomainEventChange change);
}
