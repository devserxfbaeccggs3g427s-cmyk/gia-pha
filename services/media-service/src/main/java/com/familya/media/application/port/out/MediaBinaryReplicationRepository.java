package com.familya.media.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Port ra (driven port) của kiến trúc hexagonal: theo dõi tiến độ sao
 * chép binary giữa các region.
 *
 * <p>Mỗi lần {@code ScanAndPromoteUseCase} promote media sang
 * {@code READY}, nó ghi một row {@code PENDING} vào repo này; worker
 * async sẽ sao chép binary sang region phụ rồi cập nhật {@code status}
 * ({@code SUCCEEDED} / {@code FAILED}).</p>
 */
public interface MediaBinaryReplicationRepository {

    /**
     * Ghi nhận một yêu cầu sao chép mới.
     *
     * @param mediaId      UUID media cần replicate.
     * @param sourceRegion region nguồn (vd. {@code primary}).
     * @param targetRegion region đích (vd. {@code secondary}).
     * @param sha256       SHA-256 hash của blob; dùng để kiểm tra sau
     *                     khi sao chép.
     * @param status       trạng thái ban đầu, thường {@code PENDING}.
     * @param at           mốc thời gian ghi nhận.
     */
    void record(UUID mediaId, String sourceRegion, String targetRegion, String sha256, String status, Instant at);

    /**
     * Liệt kê lịch sử sao chép của một media.
     *
     * @param mediaId UUID media.
     * @return danh sách các row; rỗng nếu media chưa từng được replicate.
     */
    List<ReplicationRow> listForMedia(UUID mediaId);

    /**
     * Một dòng lịch sử sao chép.
     *
     * @param id            UUID row.
     * @param mediaId       UUID media liên quan.
     * @param sourceRegion  region nguồn.
     * @param targetRegion  region đích.
     * @param sha256        SHA-256 hash được kiểm tra sau replicate.
     * @param status        trạng thái, vd. {@code PENDING}, {@code SUCCEEDED},
     *                      {@code FAILED}.
     * @param lastAttemptAt mốc thời gian lần thử cuối.
     */
    record ReplicationRow(UUID id, UUID mediaId, String sourceRegion, String targetRegion, String sha256, String status, Instant lastAttemptAt) { }
}
