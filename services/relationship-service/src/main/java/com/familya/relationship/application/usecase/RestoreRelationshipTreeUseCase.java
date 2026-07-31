package com.familya.relationship.application.usecase;

import com.familya.relationship.application.port.in.RestoreRelationshipTreeCommand;
import com.familya.relationship.application.port.out.RelationshipRepository;
import com.familya.relationship.domain.model.Relationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Use case khôi phục các quan hệ trong một cây đã bị thanh lọc trước đó bởi
 * {@code PurgeRelationshipTreeUseCase}. Đây là bước bồi thường (compensation)
 * của saga xóa cây.
 * <p>
 * Quy trình:
 * </p>
 * <ol>
 *   <li>Nạp toàn bộ quan hệ trong cây (bao gồm đã tombstone).</li>
 *   <li>Với mỗi quan hệ đang tombstone có thời điểm xóa khác "now" (chỉ bỏ
 *       qua các quan hệ vừa mới tombstone ở cùng timestamp), gọi
 *       {@code Repository.untombstone} để đưa cạnh về trạng thái sống.</li>
 *   <li>Tính {@code appliedAggregateVersion} lớn nhất và trả về cùng với số
 *       cạnh đã khôi phục để orchestrator xác nhận barrier.</li>
 * </ol>
 */
@Service
public class RestoreRelationshipTreeUseCase {

    /** Logger ghi nhận hoạt động. */
    private static final Logger LOG = LoggerFactory.getLogger(RestoreRelationshipTreeUseCase.class);

    /** Repository để thao tác với cơ sở dữ liệu. */
    private final RelationshipRepository repo;

    /**
     * Khởi tạo use case.
     *
     * @param repo repository quan hệ
     */
    public RestoreRelationshipTreeUseCase(RelationshipRepository repo) {
        this.repo = repo;
    }

    /**
     * Thực thi khôi phục.
     *
     * @param cmd lệnh khôi phục (chứa operationId và treeId)
     * @return kết quả gồm số quan hệ đã khôi phục và phiên bản aggregate áp dụng
     */
    @Transactional
    public Result execute(RestoreRelationshipTreeCommand cmd) {
        // Bước 1: nạp tất cả quan hệ trong cây, bao gồm cả đã tombstone để có thể khôi phục.
        List<Relationship> all = repo.listByTree(cmd.treeId(), true);
        Instant now = Instant.now();
        long maxVersion = 0L;
        int restored = 0;
        // Bước 2: duyệt qua từng quan hệ.
        for (Relationship r : all) {
            // Điều kiện "tombstonedAt != null && tombstonedAt != now" đảm bảo:
            //  - Bỏ qua các quan hệ chưa bị xóa mềm (tombstonedAt == null).
            //  - Tránh "khôi phục" nhầm các quan hệ vừa mới tombstone trong cùng
            //    transaction (nếu có thao tác xảy ra ngay trước đó với cùng timestamp).
            if (!r.tombstonedAt().equals(now) && r.tombstonedAt() != null) {
                // Gọi untombstone với version hiện tại; version + 1 sẽ được tính trong repo.
                repo.untombstone(r.id(), now, r.version());
                // Theo dõi phiên bản cao nhất để trả về cho orchestrator.
                maxVersion = Math.max(maxVersion, r.version() + 1);
                restored++;
            }
        }
        LOG.info("Restored {} relationship edges on tree {} operationId={}",
                restored, cmd.treeId(), cmd.operationId());
        return new Result(restored, maxVersion, 0L);
    }

    /**
     * Kết quả của use case, dùng để orchestrator kiểm tra barrier.
     *
     * @param affectedCount             số quan hệ đã khôi phục
     * @param appliedAggregateVersion   phiên bản aggregate đã áp dụng
     * @param appliedEpoch              epoch đã áp dụng (hiện tại = 0)
     */
    public record Result(int affectedCount, long appliedAggregateVersion, long appliedEpoch) { }
}