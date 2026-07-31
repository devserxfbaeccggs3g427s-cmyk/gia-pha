package com.familya.platform.telemetry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Tập tên metric chuẩn được sử dụng xuyên suốt các dịch vụ Family Tree.
 *
 * <p>Việc có một bộ tên metric thống nhất, được biết đến rộng rãi giúp:</p>
 * <ul>
 *   <li>Dashboard và alert có thể chia sẻ giữa các dịch vụ.</li>
 *   <li>Các truy vấn Grafana / Datadog có thể tái sử dụng.</li>
 *   <li>Phân tích hiệu năng và vận hành trở nên nhất quán.</li>
 * </ul>
 *
 * <p><b>Quy ước đặt tên:</b> Tất cả metric đều có tiền tố {@code familya.*}
 * theo sau là nhóm chức năng (mutation, outbox, consumer, projection, blob,
 * media, cleanup). Mỗi metric đều có tag {@code service} để dễ lọc theo dịch vụ.</p>
 *
 * @author Family Tree Platform Team
 */
@Component
public class PlatformMetrics {

    /** MeterRegistry dùng để đăng ký và tăng metric. */
    private final MeterRegistry registry;

    /**
     * Khởi tạo collector với {@link MeterRegistry}.
     *
     * @param registry MeterRegistry do Spring cung cấp
     */
    public PlatformMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Tăng metric đếm số lượng mutation đã được chấp nhận.
     *
     * @param service   tên dịch vụ
     * @param operation tên thao tác (vd {@code createTree})
     */
    public void mutationAccepted(String service, String operation) {
        registry.counter("familya.mutation.accepted", Tags.of("service", service, "operation", operation)).increment();
    }

    /**
     * Tăng metric đếm số lượng mutation thất bại.
     *
     * @param service   tên dịch vụ
     * @param operation tên thao tác
     * @param code      mã lỗi (vd {@code validation.failed}, {@code version.conflict})
     */
    public void mutationFailed(String service, String operation, String code) {
        registry.counter("familya.mutation.failed", Tags.of("service", service, "operation", operation, "code", code)).increment();
    }

    /**
     * Lấy {@link Timer} để đo độ trễ của mutation. Caller có thể {@code record}
     * thời gian thực hiện thao tác để tính percentile, p95, p99.
     *
     * @param service   tên dịch vụ
     * @param operation tên thao tác
     * @return {@link Timer} đã được cấu hình
     */
    public Timer mutationLatency(String service, String operation) {
        return registry.timer("familya.mutation.latency", Tags.of("service", service, "operation", operation));
    }

    /**
     * Tăng metric đếm số bản ghi outbox đã được stage.
     *
     * @param service   tên dịch vụ
     * @param eventType loại sự kiện
     */
    public void outboxStaged(String service, String eventType) {
        registry.counter("familya.outbox.staged", Tags.of("service", service, "event_type", eventType)).increment();
    }

    /**
     * Tăng metric đếm số bản ghi outbox đã được publish thành công.
     *
     * @param service   tên dịch vụ
     * @param eventType loại sự kiện
     */
    public void outboxPublished(String service, String eventType) {
        registry.counter("familya.outbox.published", Tags.of("service", service, "event_type", eventType)).increment();
    }

    /**
     * Tăng metric đếm số lần publish outbox thất bại.
     *
     * @param service   tên dịch vụ
     * @param eventType loại sự kiện
     */
    public void outboxPublishFailed(String service, String eventType) {
        registry.counter("familya.outbox.publish_failed", Tags.of("service", service, "event_type", eventType)).increment();
    }

    /**
     * Tăng metric đếm số message đã được consumer xử lý thành công.
     *
     * @param service   tên dịch vụ
     * @param eventType loại sự kiện
     */
    public void consumerProcessed(String service, String eventType) {
        registry.counter("familya.consumer.processed", Tags.of("service", service, "event_type", eventType)).increment();
    }

    /**
     * Tăng metric đếm số message đã được xử lý nhưng bị bỏ qua do trùng lặp
     * (qua cơ chế dedupe của inbox).
     *
     * @param service   tên dịch vụ
     * @param eventType loại sự kiện
     */
    public void consumerDuplicate(String service, String eventType) {
        registry.counter("familya.consumer.duplicate", Tags.of("service", service, "event_type", eventType)).increment();
    }

    /**
     * Tăng metric đếm số lần projection bị coi là cũ.
     *
     * @param service    tên dịch vụ
     * @param projection tên projection
     * @param ageSeconds tuổi của projection (giây)
     */
    public void projectionStale(String service, String projection, long ageSeconds) {
        registry.counter("familya.projection.stale", Tags.of("service", service, "projection", projection, "age_seconds", String.valueOf(ageSeconds))).increment();
    }

    /**
     * Trả về Counter đếm số mutation đã được chấp nhận, cho phép caller
     * tăng trực tiếp nếu cần (vd trong một transaction).
     *
     * @param service   tên dịch vụ
     * @param operation tên thao tác
     * @return {@link Counter} đã được cấu hình
     */
    public Counter mutationAcceptedCounter(String service, String operation) {
        return registry.counter("familya.mutation.accepted", Tags.of("service", service, "operation", operation));
    }

    /**
     * Tăng metric đếm số lần cleanup tài nguyên đã giải phóng.
     *
     * @param service tên dịch vụ
     */
    public void cleanupReleased(String service) {
        registry.counter("familya.cleanup.released", Tags.of("service", service)).increment();
    }

    /**
     * Tăng metric đếm số capability (token truy cập blob) đã được phát hành.
     *
     * @param service tên dịch vụ
     * @param outcome kết quả (vd {@code allowed}, {@code denied})
     */
    public void capabilityIssued(String service, String outcome) {
        registry.counter("familya.blob.capability_issued", Tags.of("service", service, "outcome", outcome)).increment();
    }

    /**
     * Tăng metric đếm số lần quét phương tiện (media scan) đã hoàn tất.
     *
     * @param service tên dịch vụ
     * @param verdict kết quả quét (vd {@code clean}, {@code infected})
     */
    public void scanCompleted(String service, String verdict) {
        registry.counter("familya.media.scan_completed", Tags.of("service", service, "verdict", verdict)).increment();
    }
}
