package com.familya.relationship.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Sự kiện phát ra khi một quan hệ gia phả bị đánh dấu xóa mềm (tombstoned).
 * <p>
 * Sự kiện này được phát hành bởi:
 * </p>
 * <ul>
 *   <li>Use case {@code TombstoneRelationshipUseCase} khi người dùng xóa trực tiếp
 *       một quan hệ.</li>
 *   <li>Use case {@code DisableMemberRelationshipsUseCase} khi saga xóa thành viên
 *       cần đồng thời "vô hiệu hóa" mọi quan hệ có liên quan.</li>
 *   <li>Use case {@code PurgeRelationshipTreeUseCase} khi saga xóa toàn bộ cây.</li>
 * </ul>
 *
 * <p>
 * Consumer của sự kiện này có thể:
 * </p>
 * <ul>
 *   <li>Ẩn quan hệ khỏi các chỉ mục tìm kiếm.</li>
 *   <li>Cập nhật projection dạng summary/aggregation.</li>
 *   <li>Lưu vết cho mục đích audit.</li>
 * </ul>
 *
 * <p>
 * Lưu ý: sự kiện tombstone <b>không</b> chứa toàn bộ snapshot của quan hệ vì
 * định danh {@code relationshipId} đã đủ để truy ngược lại dòng dữ liệu gốc
 * trong database của Relationship service.
 * </p>
 */
public final class RelationshipTombstoned extends RelationshipEvent {
    /** Định danh của quan hệ bị đánh dấu xóa mềm. */
    private final UUID relationshipId;
    /** Định danh cây gia phả chứa quan hệ. */
    private final UUID treeId;
    /** Số thứ tự lệnh theo cây. */
    private final long commandSeq;
    /** Thời điểm sự kiện xảy ra. */
    private final Instant occurredAt;

    /**
     * Khởi tạo sự kiện xóa mềm.
     *
     * @param relationshipId định danh quan hệ bị xóa, không null
     * @param treeId         định danh cây gia phả, không null
     * @param commandSeq     số thứ tự lệnh theo cây
     * @param occurredAt     thời điểm sự kiện xảy ra, không null
     */
    public RelationshipTombstoned(UUID relationshipId, UUID treeId, long commandSeq, Instant occurredAt) {
        this.relationshipId = relationshipId;
        this.treeId = treeId;
        this.commandSeq = commandSeq;
        this.occurredAt = occurredAt;
    }

    /** {@inheritDoc} */
    @Override public UUID treeId() { return treeId; }

    /** {@inheritDoc} */
    @Override public String eventType() { return "RelationshipTombstoned"; }

    /** {@inheritDoc} */
    @Override public int eventVersion() { return 1; }

    /** {@inheritDoc} */
    @Override public Instant occurredAt() { return occurredAt; }

    /** {@inheritDoc} */
    @Override public long commandSeq() { return commandSeq; }

    /** @return định danh của quan hệ bị đánh dấu xóa mềm. */
    public UUID relationshipId() { return relationshipId; }
}