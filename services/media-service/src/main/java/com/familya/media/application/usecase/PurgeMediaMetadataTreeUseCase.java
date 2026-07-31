package com.familya.media.application.usecase;

import com.familya.media.application.port.in.PurgeMediaMetadataTreeCommand;
import com.familya.media.application.port.out.MediaReferenceRepository;
import com.familya.media.application.port.out.MediaRepository;
import com.familya.media.application.port.out.MediaRetentionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Use case tham gia vào {@code delete-tree Saga} ở phía media-service.
 *
 * <p>Luồng nghiệp vụ chính:</p>
 * <ol>
 *   <li>Tombstone (xóa mềm) toàn bộ {@code MediaAsset} thuộc tree.</li>
 *   <li>Nếu {@code placeRetentionHolds=true}: đặt retention hold
 *       {@code RETENTION_GRACE} (30 ngày) cho từng media, với lý do
 *       {@code delete-tree-saga:{operationId}} để truy vết.</li>
 *   <li>Xóa mọi tham chiếu media loại {@code MEMBER} và {@code EVENT}
 *       trỏ tới target thuộc tree.</li>
 * </ol>
 *
 * <p>Binary vật lý KHÔNG bị xóa trong use case này; cleanup worker sẽ
 * xóa sau khi retention hold hết hạn (mặc định 30 ngày).</p>
 *
 * <p>{@code RETENTION_GRACE = 30 ngày} là hằng số thiết kế nhằm cân
 * bằng giữa tuân thủ chính sách "khôi phục trong thời gian ân hạn" và
 * tiết kiệm dung lượng blob.</p>
 */
@Service
public class PurgeMediaMetadataTreeUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(PurgeMediaMetadataTreeUseCase.class);

    /** Thời gian ân hạn giữ binary trước khi cleanup worker được xóa. */
    static final Duration RETENTION_GRACE = Duration.ofDays(30);

    private final MediaRepository media;
    private final MediaReferenceRepository refs;
    private final MediaRetentionRepository retention;

    public PurgeMediaMetadataTreeUseCase(MediaRepository media,
                                         MediaReferenceRepository refs,
                                         MediaRetentionRepository retention) {
        this.media = media;
        this.refs = refs;
        this.retention = retention;
    }

    /**
     * Thực thi use case.
     *
     * @param cmd lệnh purge; xem {@link PurgeMediaMetadataTreeCommand}.
     * @return {@link Result} mô tả số media đã tombstone, phiên bản
     *         aggregate và epoch Saga.
     */
    @Transactional
    public Result execute(PurgeMediaMetadataTreeCommand cmd) {
        Instant now = Instant.now();
        // Bước 1: tombstone mọi MediaAsset của tree.
        int tombstoned = media.bulkTombstoneByTree(cmd.treeId(), now);
        if (cmd.placeRetentionHolds()) {
            // Bước 2: đặt retention hold với deadline now + RETENTION_GRACE;
            // lý do gắn operationId giúp bước restore có thể giải phóng
            // đúng các hold của Saga.
            retention.bulkPlaceHoldByTree(cmd.treeId(), now.plus(RETENTION_GRACE),
                    "delete-tree-saga:" + cmd.operationId());
        }
        // Bước 3: dọn tham chiếu MEMBER/EVENT trỏ tới target thuộc tree;
        // ALBUM không xóa ở đây vì bản thân album đã tombstone.
        int cleared = refs.bulkClearByTree(cmd.treeId(), List.of("MEMBER", "EVENT"));
        LOG.info("Bulk-tombstoned {} media rows and cleared {} references on tree {} operationId={}",
                tombstoned, cleared, cmd.treeId(), cmd.operationId());
        // appliedAggregateVersion chặn giá trị âm từ upstream để tránh
        // Saga bị từ chối bởi validator.
        return new Result(tombstoned, Math.max(0L, cmd.targetAggregateVersion()), cmd.targetEpoch());
    }

    /**
     * Kết quả trả về cho Saga coordinator.
     *
     * @param affectedCount          số media đã tombstone.
     * @param appliedAggregateVersion phiên bản aggregate được áp dụng
     *                               (đã chặn giá trị âm).
     * @param appliedEpoch           epoch Saga.
     */
    public record Result(int affectedCount, long appliedAggregateVersion, long appliedEpoch) { }
}