package com.familya.relationship.application.usecase;

import com.familya.relationship.application.port.in.LoadRelationshipManifestCommand;
import com.familya.relationship.application.port.out.RelationshipRepository;
import com.familya.relationship.domain.model.Relationship;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case nạp một manifest các quan hệ vào cơ sở dữ liệu.
 * <p>
 * Manifest thường được tạo ra trong quá trình migration từ hệ thống cũ. Use
 * case này được thiết kế để <b>idempotent</b>:
 * </p>
 * <ul>
 *   <li>Nếu một {@code relationshipId} đã tồn tại thì bỏ qua (tính là duplicate).</li>
 *   <li>Nếu việc chèn ném {@link DuplicateKeyException} (race condition giữa
 *       kiểm tra và insert) thì cũng tính là duplicate.</li>
 * </ul>
 *
 * <p>
 * Nhờ vậy có thể chạy lại (replay) manifest an toàn mà không tạo dữ liệu trùng.
 * </p>
 */
@Service
public class LoadRelationshipManifestUseCase {

    /** Logger ghi nhận hoạt động. */
    private static final Logger LOG = LoggerFactory.getLogger(LoadRelationshipManifestUseCase.class);

    /** Repository thao tác với DB. */
    private final RelationshipRepository repo;
    /** Bộ đếm metric. */
    private final PlatformMetrics metrics;

    /**
     * Khởi tạo use case.
     *
     * @param repo    repository
     * @param metrics bộ đếm metric
     */
    public LoadRelationshipManifestUseCase(RelationshipRepository repo, PlatformMetrics metrics) {
        this.repo = repo;
        this.metrics = metrics;
    }

    /**
     * Thực thi nạp manifest.
     * <p>
     * Thuật toán:
     * </p>
     * <ol>
     *   <li>Ghi nhận metric "mutation được chấp nhận".</li>
     *   <li>Khởi tạo bộ đếm {@code loaded} và {@code duplicates}.</li>
     *   <li>Với mỗi dòng trong manifest:
     *     <ul>
     *       <li>Nếu ID đã tồn tại trong DB → tăng {@code duplicates}, bỏ qua.</li>
     *       <li>Tạo aggregate mới với version = 0L, tombstonedAt = null.</li>
     *       <li>Cố gắng insert; nếu thành công tăng {@code loaded}.</li>
     *       <li>Nếu gặp {@link DuplicateKeyException} → tăng {@code duplicates}
     *           (race condition an toàn).</li>
     *     </ul>
     *   </li>
     *   <li>Trả về {@link LoadResult} tổng hợp.</li>
     * </ol>
     *
     * @param cmd lệnh nạp manifest
     * @return kết quả gồm số đã nạp và số trùng lặp
     */
    @Transactional
    public LoadResult execute(LoadRelationshipManifestCommand cmd) {
        metrics.mutationAccepted("relationship-service", "loadRelationshipManifest");
        int loaded = 0, duplicates = 0;
        // Duyệt từng dòng trong manifest theo thứ tự.
        for (var line : cmd.relationships()) {
            // Bước a: kiểm tra nhanh xem ID đã tồn tại chưa để tránh insert thừa.
            if (repo.findById(line.relationshipId()).isPresent()) {
                duplicates++;
                continue;   // Bỏ qua dòng đã có.
            }
            // Bước b: tạo aggregate với version=0L và tombstonedAt=null.
            // revision và createdAt lấy từ manifest để bảo toàn thông tin nguồn.
            Relationship rel = new Relationship(
                    line.relationshipId(), cmd.treeId(), line.kind(),
                    line.fromMemberId(), line.toMemberId(), line.metadataJson(),
                    line.revision(), line.createdAt(), null, 0L);
            try {
                // Bước c: cố gắng insert.
                repo.insert(rel);
                loaded++;
            } catch (DuplicateKeyException dup) {
                // Race condition: dòng được insert bởi transaction khác giữa
                // lúc findById và insert. Bỏ qua một cách an toàn.
                duplicates++;
            }
        }
        LOG.info("Loaded relationships tree={} loaded={} duplicates={}", cmd.treeId(), loaded, duplicates);
        return new LoadResult(loaded, duplicates);
    }

    /**
     * Kết quả nạp manifest.
     *
     * @param loaded     số quan hệ đã nạp thành công
     * @param duplicates số quan hệ bị bỏ qua vì đã tồn tại
     */
    public record LoadResult(int loaded, int duplicates) { }
}