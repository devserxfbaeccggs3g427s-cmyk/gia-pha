package com.familya.media.application.usecase;

import com.familya.media.application.port.in.RestoreMediaMetadataTreeCommand;
import com.familya.media.application.port.out.MediaRepository;
import com.familya.media.application.port.out.MediaRetentionRepository;
import com.familya.media.domain.model.MediaAsset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Use case bù trừ (compensation) khi {@code delete-tree Saga} fail trước
 * ranh giới không-thể-đảo: khôi phục lại metadata media đã bị tombstone.
 *
 * <p>Luồng nghiệp vụ chính:</p>
 * <ol>
 *   <li>Liệt kê toàn bộ {@code MediaAsset} của tree (bao gồm cả đã
 *       tombstone).</li>
 *   <li>Với mỗi media đang tombstone: giải phóng retention hold đã đặt
 *       bởi {@code operationId} (để cleanup worker không xóa binary).</li>
 *   <li>Tạo bản ghi mới với {@code tombstonedAt = null}, version +1, các
 *       trường khác giữ nguyên.</li>
 *   <li>Trả về {@code maxVersion} để Saga theo dõi phiên bản aggregate
 *       áp dụng.</li>
 * </ol>
 */
@Service
public class RestoreMediaMetadataTreeUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(RestoreMediaMetadataTreeUseCase.class);

    private final MediaRepository media;
    private final MediaRetentionRepository retention;

    public RestoreMediaMetadataTreeUseCase(MediaRepository media, MediaRetentionRepository retention) {
        this.media = media;
        this.retention = retention;
    }

    /**
     * Thực thi use case.
     *
     * @param cmd lệnh khôi phục; xem {@link RestoreMediaMetadataTreeCommand}.
     * @return {@link Result} mô tả số media đã khôi phục và phiên bản
     *         aggregate cao nhất đã áp dụng.
     */
    @Transactional
    public Result execute(RestoreMediaMetadataTreeCommand cmd) {
        // Bước 1: lấy tất cả media của tree, kể cả tombstone (true).
        List<MediaAsset> all = media.listByTree(cmd.treeId(), true);
        Instant now = Instant.now();
        long maxVersion = 0L;
        int restored = 0;
        for (MediaAsset asset : all) {
            // Bỏ qua media chưa từng bị tombstone.
            if (asset.tombstonedAt() == null) continue;
            // Bước 2: giải phóng retention hold đặt bởi operationId của
            // Saga purge; nhờ đó cleanup worker không xóa binary.
            // Release any retention hold placed by the purge step so the
            // cleanup worker does not delete binaries.
            retention.releaseHoldForOperation(asset.id(), cmd.operationId());
            // Bước 3: clear tombstonedAt, tăng version, giữ các trường
            // còn lại; updatedAt = now.
            MediaAsset alive = new MediaAsset(
                    asset.id(), asset.treeId(), asset.albumId(), asset.ownerUserId(),
                    asset.kind(), asset.mimeType(), asset.byteSize(), asset.sha256(),
                    asset.originalFilename(), asset.status(), asset.quarantinePath(),
                    asset.promoted(), asset.retentionHoldUntil(), null,
                    asset.createdAt(), now, asset.version() + 1);
            media.update(alive);
            // Theo dõi phiên bản cao nhất để trả về Saga.
            maxVersion = Math.max(maxVersion, alive.version());
            restored++;
        }
        LOG.info("Restored {} media metadata rows on tree {} operationId={}",
                restored, cmd.treeId(), cmd.operationId());
        return new Result(restored, maxVersion, 0L);
    }

    /**
     * Kết quả trả về cho Saga coordinator.
     *
     * @param affectedCount          số media đã khôi phục.
     * @param appliedAggregateVersion phiên bản aggregate cao nhất đã áp
     *                               dụng trong lần restore.
     * @param appliedEpoch           epoch (luôn {@code 0} vì restore
     *                               không thay đổi epoch).
     */
    public record Result(int affectedCount, long appliedAggregateVersion, long appliedEpoch) { }
}