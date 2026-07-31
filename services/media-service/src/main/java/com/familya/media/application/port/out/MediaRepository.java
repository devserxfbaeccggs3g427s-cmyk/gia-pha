package com.familya.media.application.port.out;

import com.familya.media.domain.model.MediaAsset;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port ra (driven port) của kiến trúc hexagonal: kho lưu trữ tập trung
 * cho {@code MediaAsset}.
 *
 * <p>Hợp đồng chính:</p>
 * <ul>
 *   <li>Mọi thao tác ghi có {@code expectedVersion} để áp dụng
 *       optimistic concurrency; lệch sẽ ném
 *       {@code OptimisticConcurrencyException}.</li>
 *   <li>Các phương thức {@code mark*()} và {@code claimForProcessing()}
 *       được dùng bởi pipeline xử lý media (verify → scan → promote).</li>
 *   <li>Các phương thức default giúp triển khai đơn giản vẫn hỗ trợ bulk
 *       tombstone; triển khai JDBC nên override để đảm bảo atomicity.</li>
 * </ul>
 */
public interface MediaRepository {

    /**
     * Chèn mới một media asset.
     *
     * @param asset bản ghi media cần chèn; phải có {@code id} chưa tồn
     *              tại trong kho.
     */
    void insert(MediaAsset asset);

    /**
     * Tra cứu media theo id.
     *
     * @param id UUID media.
     * @return {@code Optional} chứa asset; rỗng nếu không tồn tại.
     */
    Optional<MediaAsset> findById(UUID id);

    /**
     * Liệt kê media thuộc một family-tree.
     *
     * @param treeId UUID family-tree.
     * @param includeTombstoned {@code true} để bao gồm media đã tombstone.
     * @return danh sách media; có thể rỗng nhưng không null.
     */
    List<MediaAsset> listByTree(UUID treeId, boolean includeTombstoned);

    /**
     * Liệt kê media thuộc một album.
     *
     * @param albumId UUID album.
     * @param includeTombstoned {@code true} để bao gồm media đã tombstone.
     * @return danh sách media; rỗng nếu album không có media.
     */
    List<MediaAsset> listByAlbum(UUID albumId, boolean includeTombstoned);

    /**
     * Cập nhật media asset.
     *
     * @param asset bản ghi với {@code version} mới.
     */
    void update(MediaAsset asset);

    /**
     * Tombstone một media.
     *
     * @param id UUID media.
     * @param at mốc thời gian tombstone.
     * @param expectedVersion phiên bản kỳ vọng; lệch sẽ ném
     *                       {@code OptimisticConcurrencyException}.
     */
    void tombstone(UUID id, Instant at, long expectedVersion);

    /**
     * Liệt kê media được cập nhật sau một watermark, dùng cho
     * reconciliation / đồng bộ downstream.
     *
     * @param watermark mốc thời gian (thường là {@code updatedAt}).
     * @param limit     số bản ghi tối đa.
     * @return danh sách media; rỗng nếu không có.
     */
    List<MediaAsset> listAfter(Instant watermark, int limit);

    /**
     * Atomically claim a media row for processing; returns true if the row was claimed.
     *
     * <p>Dùng để đảm bảo chỉ một worker xử lý media tại một thời điểm;
     * triển khai thường thực hiện bằng {@code UPDATE ... WHERE status =
     * 'PENDING'} và trả về số dòng affected.</p>
     *
     * @param mediaId         UUID media.
     * @param expectedVersion phiên bản kỳ vọng; lệch sẽ ném.
     * @return {@code true} nếu claim thành công, {@code false} nếu media
     *         đang được xử lý bởi worker khác.
     */
    boolean claimForProcessing(UUID mediaId, long expectedVersion);

    /**
     * Chuyển media sang trạng thái {@code SCANNING}.
     *
     * @param mediaId         UUID media.
     * @param expectedVersion phiên bản kỳ vọng.
     * @param at              mốc thời gian.
     */
    void markScanning(UUID mediaId, long expectedVersion, Instant at);

    /**
     * Chuyển media sang trạng thái {@code READY} (sạch).
     *
     * @param mediaId         UUID media.
     * @param expectedVersion phiên bản kỳ vọng.
     * @param at              mốc thời gian.
     */
    void markReady(UUID mediaId, long expectedVersion, Instant at);

    /**
     * Chuyển media sang trạng thái {@code FAILED} (lý do bất kỳ: sha256
     * mismatch, scanner fail, infected, ...).
     *
     * @param mediaId         UUID media.
     * @param expectedVersion phiên bản kỳ vọng.
     * @param at              mốc thời gian.
     * @param reason          mã lý do (vd. {@code sha256 mismatch},
     *                        {@code scanner-unavailable},
     *                        {@code infected}).
     */
    void markFailed(UUID mediaId, long expectedVersion, Instant at, String reason);

    /**
     * Sets the quarantine path used by scanner/thumbnail adapters.
     *
     * @param mediaId         UUID media.
     * @param path            đường dẫn exact-path trong blob store.
     * @param expectedVersion phiên bản kỳ vọng.
     */
    void recordQuarantinePath(UUID mediaId, String path, long expectedVersion);

    /**
     * Bulk tombstone every non-tombstoned media row in the tree.
     *
     * <p>Triển khai mặc định duyệt {@link #listByTree(UUID, boolean)} và
     * gọi {@link #tombstone(UUID, Instant, long)} tuần tự; triển khai
     * JDBC nên override bằng một transaction duy nhất để tránh lệch
     * trạng thái giữa các row.</p>
     *
     * @param treeId UUID family-tree.
     * @param at     mốc thời gian tombstone.
     * @return số media đã tombstone.
     */
    default int bulkTombstoneByTree(UUID treeId, Instant at) {
        int n = 0;
        for (MediaAsset a : listByTree(treeId, false)) {
            tombstone(a.id(), at, a.version());
            n++;
        }
        return n;
    }

    /**
     * Place a retention hold on every media row in the tree.
     *
     * <p>Default delegates to MediaRetentionRepository through the caller;
     * overridden by JdbcMediaRepository for atomicity.</p>
     *
     * @param treeId    UUID family-tree.
     * @param holdUntil deadline giữ binary.
     * @param reason    lý do đặt hold.
     * @return số row đã đặt hold.
     */
    default int bulkPlaceRetentionHold(UUID treeId, Instant holdUntil, String reason) {
        // Default delegates to MediaRetentionRepository through the caller;
        // overridden by JdbcMediaRepository for atomicity.
        return 0;
    }
}