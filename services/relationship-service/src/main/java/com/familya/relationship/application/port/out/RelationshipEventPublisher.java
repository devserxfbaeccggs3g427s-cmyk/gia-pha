package com.familya.relationship.application.port.out;

import com.familya.relationship.domain.event.RelationshipEvent;

/**
 * Cổng (port) ra phía messaging, chịu trách nhiệm phát hành sự kiện miền của
 * Relationship service.
 * <p>
 * Triển khai mặc định ({@code OutboxRelationshipEventPublisher}) sử dụng mẫu
 * <b>transactional outbox</b>: thay vì gửi thẳng vào Kafka, sự kiện được ghi
 * vào bảng outbox trong cùng transaction với lệnh ghi dữ liệu. Một worker
 * riêng sẽ đọc outbox và đẩy lên Kafka, đảm bảo:
 * </p>
 * <ul>
 *   <li>Sự kiện chỉ được phát khi transaction nghiệp vụ commit thành công.</li>
 *   <li>Tránh mất sự kiện nếu Kafka tạm thời không khả dụng.</li>
 *   <li>Hỗ trợ replay khi cần tái dựng projection.</li>
 * </ul>
 */
public interface RelationshipEventPublisher {

    /**
     * Phát hành một sự kiện miền. Sự kiện sẽ được ghi vào outbox (không gọi
     * trực tiếp Kafka) và sẽ được worker xuất bản ngay sau khi transaction
     * nghiệp vụ commit.
     *
     * @param event sự kiện miền cần phát hành, không null
     */
    void publish(RelationshipEvent event);
}