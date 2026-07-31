package com.familya.search.adapter.out.projection;

import com.familya.search.application.port.out.SearchAuthRepository;
import com.familya.search.application.port.out.SearchAuthorization;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Triển khai {@link SearchAuthorization} dựa trên projection ủy quyền
 * đã được các consumer Kafka cập nhật.
 *
 * <p>Logic quyết định:</p>
 * <ol>
 *   <li>Không tìm thấy bản ghi → {@link State#ABSENT}.</li>
 *   <li>Bản ghi đã bị thu hồi → {@link State#DENY}.</li>
 *   <li>Phiên bản projection cũ hơn kỳ vọng → {@link State#STALE}.</li>
 *   <li>Các trường hợp còn lại → {@link State#ALLOW}.</li>
 * </ol>
 */
@Component
public class ProjectionSearchAuthorization implements SearchAuthorization {

    private final SearchAuthRepository repo;

    /**
     * Khởi tạo với cổng đọc projection ủy quyền.
     *
     * @param repo cổng đọc {@link SearchAuthRepository}.
     */
    public ProjectionSearchAuthorization(SearchAuthRepository repo) {
        this.repo = repo;
    }

    /**
     * Đánh giá quyền truy cập dựa trên projection ủy quyền.
     *
     * @param treeId           định danh cây gia phả.
     * @param userId           định danh người dùng.
     * @param expectedRevision phiên bản cây mà client kỳ vọng.
     * @return {@link Decision} mô tả trạng thái và lý do.
     */
    @Override
    public Decision authorize(UUID treeId, UUID userId, long expectedRevision) {
        Optional<SearchAuthRepository.AuthRow> row = repo.find(treeId, userId);
        // Không có bản ghi - chưa từng thấy user trên cây này.
        if (row.isEmpty()) {
            return new Decision(State.ABSENT, "no projection row");
        }
        var r = row.get();
        // Bản ghi tồn tại nhưng đã bị thu hồi hoặc role null - từ chối.
        if (r.isRevoked()) {
            return new Decision(State.DENY, "revoked");
        }
        // Phiên bản projection cũ hơn client kỳ vọng - cần đợi projection cập nhật.
        if (r.revision() < expectedRevision) {
            return new Decision(State.STALE, "projection revision " + r.revision() + " < expected " + expectedRevision);
        }
        return new Decision(State.ALLOW, "ok");
    }
}
