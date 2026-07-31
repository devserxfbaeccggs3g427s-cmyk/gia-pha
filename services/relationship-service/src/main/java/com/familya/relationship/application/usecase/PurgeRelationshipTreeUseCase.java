package com.familya.relationship.application.usecase;

import com.familya.relationship.application.port.in.PurgeRelationshipTreeCommand;
import com.familya.relationship.application.port.out.RelationshipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Use case tham gia bước "PURGE" của saga xóa cây (Tree Access service).
 * <p>
 * Nhiệm vụ: đánh dấu xóa mềm (tombstone) <b>mọi quan hệ đang hoạt động</b> trong
 * cây mục tiêu, đồng thời trả về phiên bản aggregate và epoch đã áp dụng để
 * orchestrator kiểm tra barrier quyết định commit/rollback.
 * </p>
 *
 * <p>
 * Thuật toán:
 * </p>
 * <ol>
 *   <li>Ghi nhận timestamp hiện tại (dùng cho tất cả các tombstone).</li>
 *   <li>Ủy quyền cho {@code Repository.bulkTombstoneByTree} để xử lý hàng loạt.</li>
 *   <li>Tính {@code appliedAggregateVersion} an toàn (không âm) và trả về.</li>
 * </ol>
 */
@Service
public class PurgeRelationshipTreeUseCase {

    /** Logger ghi nhận hoạt động. */
    private static final Logger LOG = LoggerFactory.getLogger(PurgeRelationshipTreeUseCase.class);

    /** Repository để thao tác với DB. */
    private final RelationshipRepository repo;

    /**
     * Khởi tạo use case.
     *
     * @param repo repository
     */
    public PurgeRelationshipTreeUseCase(RelationshipRepository repo) {
        this.repo = repo;
    }

    /**
     * Thực thi lệnh thanh lọc cây.
     *
     * @param cmd lệnh purge
     * @return kết quả gồm số quan hệ bị ảnh hưởng và các phiên bản áp dụng
     */
    @Transactional
    public Result execute(PurgeRelationshipTreeCommand cmd) {
        // Bước 1: timestamp dùng chung cho mọi tombstone trong lần purge này.
        Instant now = Instant.now();
        // Bước 2: ủy quyền cho repository xử lý hàng loạt.
        // bulkTombstoneByTree trả về số quan hệ đã bị đánh tombstone.
        int affected = repo.bulkTombstoneByTree(cmd.treeId(), now);
        LOG.info("Bulk-tombstoned {} relationships on tree {} operationId={}",
                affected, cmd.treeId(), cmd.operationId());
        // Bước 3: appliedAggregateVersion lấy tối đa của target và 0 để không âm.
        return new Result(affected, Math.max(0L, cmd.targetAggregateVersion()), cmd.targetEpoch());
    }

    /**
     * Kết quả tham gia saga.
     *
     * @param affectedCount             số quan hệ đã bị tombstone
     * @param appliedAggregateVersion   phiên bản aggregate áp dụng
     * @param appliedEpoch              epoch áp dụng
     */
    public record Result(int affectedCount, long appliedAggregateVersion, long appliedEpoch) { }
}