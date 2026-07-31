package com.familya.relationship.domain.event;

import com.familya.relationship.domain.model.Relationship;

import java.time.Instant;
import java.util.UUID;

/**
 * Sự kiện phát ra khi một quan hệ gia phả mới được tạo thành công.
 * <p>
 * Đây là sự kiện <b>append-only</b> - aggregate không thể bị "tạo lại" mà chỉ
 * có thể bị đánh dấu xóa mềm (tombstone) qua {@link RelationshipTombstoned}.
 * Do đó, các consumer của sự kiện này chỉ cần insert các thông tin cần thiết
 * vào projection tương ứng (ví dụ: chỉ mục tìm kiếm, bảng tổng hợp).
 * </p>
 *
 * <h2>Quan hệ SPOUSE</h2>
 * <p>
 * Đối với quan hệ vợ chồng, use case {@code CreateRelationshipUseCase} sẽ phát
 * ra <b>hai</b> sự kiện {@code RelationshipCreated}: một cho cạnh chính và một
 * cho cạnh đối xứng (mirror). Hai sự kiện này chia sẻ cùng {@code commandSeq}
 * để duy trì tính nguyên tử theo cây.
 * </p>
 */
public final class RelationshipCreated extends RelationshipEvent {
    /** Định danh duy nhất của quan hệ vừa được tạo. */
    private final UUID relationshipId;
    /** Định danh cây gia phả chứa quan hệ. */
    private final UUID treeId;
    /** Loại quan hệ (PARENT_CHILD / SPOUSE / ADOPTION). */
    private final Relationship.Kind kind;
    /** Thành viên phía nguồn của cạnh. */
    private final UUID fromMemberId;
    /** Thành viên phía đích của cạnh. */
    private final UUID toMemberId;
    /** Chuỗi JSON metadata bổ sung (có thể null). */
    private final String metadataJson;
    /** Số thứ tự lệnh theo cây (chia sẻ giữa cạnh chính và mirror). */
    private final long commandSeq;
    /** Thời điểm xảy ra sự kiện. */
    private final Instant occurredAt;

    /**
     * Khởi tạo sự kiện tạo quan hệ.
     *
     * @param relationshipId định danh quan hệ, không null
     * @param treeId         định danh cây gia phả, không null
     * @param kind           loại quan hệ, không null
     * @param fromMemberId   thành viên phía nguồn, không null
     * @param toMemberId     thành viên phía đích, không null
     * @param metadataJson   chuỗi JSON metadata bổ sung, có thể null
     * @param commandSeq     số thứ tự lệnh theo cây
     * @param occurredAt     thời điểm sự kiện xảy ra, không null
     */
    public RelationshipCreated(UUID relationshipId, UUID treeId, Relationship.Kind kind,
                                UUID fromMemberId, UUID toMemberId, String metadataJson,
                                long commandSeq, Instant occurredAt) {
        this.relationshipId = relationshipId;
        this.treeId = treeId;
        this.kind = kind;
        this.fromMemberId = fromMemberId;
        this.toMemberId = toMemberId;
        this.metadataJson = metadataJson;
        this.commandSeq = commandSeq;
        this.occurredAt = occurredAt;
    }

    /** {@inheritDoc} */
    @Override public UUID treeId() { return treeId; }

    /** {@inheritDoc} */
    @Override public String eventType() { return "RelationshipCreated"; }

    /** {@inheritDoc} */
    @Override public int eventVersion() { return 1; }

    /** {@inheritDoc} */
    @Override public Instant occurredAt() { return occurredAt; }

    /** {@inheritDoc} */
    @Override public long commandSeq() { return commandSeq; }

    /** @return định danh của quan hệ vừa được tạo. */
    public UUID relationshipId() { return relationshipId; }

    /** @return loại quan hệ (PARENT_CHILD / SPOUSE / ADOPTION). */
    public Relationship.Kind kind() { return kind; }

    /** @return định danh thành viên phía nguồn. */
    public UUID fromMemberId() { return fromMemberId; }

    /** @return định danh thành viên phía đích. */
    public UUID toMemberId() { return toMemberId; }

    /** @return chuỗi JSON metadata bổ sung (có thể null). */
    public String metadataJson() { return metadataJson; }
}