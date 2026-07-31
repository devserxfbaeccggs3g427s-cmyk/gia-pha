package com.familya.sharing.application.usecase;

import com.familya.sharing.application.port.out.AllowlistedProjectionRepository;
import com.familya.sharing.application.port.out.AllowlistedProjectionRepository.ShareScope;
import com.familya.sharing.application.port.out.ShareChangePublisher;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Use case tái tạo (rebuild) một projection công khai đã được lọc trắng
 * (allowlisted) cho sharing-service.
 * <p>
 * Chỉ những trường nằm trong danh sách cho phép mới được giữ lại &mdash; mọi
 * khoá ngoài danh sách sẽ bị tầng ghi từ chối. Sau khi tái tạo xong, use
 * case sẽ:
 * <ol>
 *     <li>Lưu projection mới cùng watermark.</li>
 *     <li>Cập nhật watermark trong bảng {@code share_watermark} theo từng domain.</li>
 *     <li>Phát hành sự kiện {@code ShareProjectionRebuilt} qua outbox.</li>
 * </ol>
 *
 * @author gia-pha platform team
 */
@Service
public class RebuildPublicProjectionUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(RebuildPublicProjectionUseCase.class);

    private final AllowlistedProjectionRepository projections;
    private final ShareChangePublisher publisher;
    private final PlatformMetrics metrics;

    /**
     * Khởi tạo use case với các phụ thuộc.
     *
     * @param projections kho lưu trữ projection allowlist.
     * @param publisher   cổng phát hành sự kiện.
     * @param metrics     bộ thu thập metric.
     */
    public RebuildPublicProjectionUseCase(AllowlistedProjectionRepository projections,
                                           ShareChangePublisher publisher,
                                           PlatformMetrics metrics) {
        this.projections = projections;
        this.publisher = publisher;
        this.metrics = metrics;
    }

    /**
     * Thực thi tái tạo projection công khai.
     *
     * @param treeId       định danh cây gia phả.
     * @param scope        phạm vi projection.
     * @param targetId     định danh mục tiêu (có thể {@code null} với {@code TREE}).
     * @param newWatermark phiên bản watermark mới.
     * @return giá trị watermark mới đã được áp dụng.
     */
    @Transactional
    public long execute(UUID treeId, ShareScope scope, UUID targetId, long newWatermark) {
        // Bước 1: Đọc projection đã được lưu cho phạm vi/mục tiêu tương ứng.
        // Dùng switch để tường minh &mdash; có thể thay bằng projection.readPublicProjection(...) trực tiếp.
        Map<String, Object> value = switch (scope) {
            case TREE -> projections.readPublicProjection(treeId, ShareScope.TREE, targetId);
            case MEMBER -> projections.readPublicProjection(treeId, ShareScope.MEMBER, targetId);
            case MEDIA -> projections.readPublicProjection(treeId, ShareScope.MEDIA, targetId);
            case EVENT -> projections.readPublicProjection(treeId, ShareScope.EVENT, targetId);
        };

        // Bước 2: Nếu không tìm thấy projection, dùng map rỗng &mdash; vẫn ghi để duy trì watermark.
        if (value == null) {
            value = Map.of();
        }

        // Bước 3: Lưu projection mới kèm watermark.
        Instant now = Instant.now();
        projections.savePublicProjection(treeId, scope, targetId, value, newWatermark, now);

        // Bước 4: Cập nhật watermark cho domain tương ứng (dùng tên scope viết thường).
        projections.advanceWatermark(treeId, scope.name().toLowerCase(), newWatermark, now);

        // Bước 5: Phát hành sự kiện ShareProjectionRebuilt qua outbox.
        publisher.projectionRebuilt(treeId, newWatermark);

        // Bước 6: Ghi nhận metric.
        metrics.mutationAccepted("sharing-service", "rebuildProjection");

        LOG.info("Rebuilt projection tree={} scope={} target={} watermark={}", treeId, scope, targetId, newWatermark);
        return newWatermark;
    }
}