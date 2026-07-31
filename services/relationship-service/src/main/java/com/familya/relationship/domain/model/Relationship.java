package com.familya.relationship.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate gốc (aggregate root) biểu diễn một cạnh (edge) trong đồ thị gia phả.
 * <p>
 * Mỗi {@code Relationship} mang khóa logic gồm bốn thành phần:
 * {@code (treeId, kind, fromMember, toMember)}. Cơ sở dữ liệu đảm bảo ràng buộc
 * duy nhất cục bộ trên bộ bốn này nên không thể tồn tại hai cạnh trùng lặp trong
 * cùng một cây gia phả.
 * </p>
 *
 * <h2>Ba loại quan hệ được hỗ trợ</h2>
 * <ul>
 *   <li>{@link Kind#PARENT_CHILD}: {@code fromMember} là cha/mẹ, {@code toMember}
 *       là con. Loại quan hệ này tạo thành cấu trúc cây và bị ràng buộc bởi
 *       quy tắc không tạo vòng (cycle) và phải có hướng từ tổ tiên đến hậu duệ.</li>
 *   <li>{@link Kind#SPOUSE}: Quan hệ vợ chồng được lưu trữ đối xứng; bản ghi
 *       chính và bản ghi phản chiếu (mirror) được chèn cùng nhau trong cùng một
 *       transaction. Do đó một cạnh vợ chồng luôn xuất hiện hai dòng trong bảng.</li>
 *   <li>{@link Kind#ADOPTION}: {@code fromMember} là người nhận nuôi,
 *       {@code toMember} là người được nhận nuôi. Quan hệ này không làm thay đổi
 *       "thế hệ" (generation) của đứa trẻ so với dòng máu sinh học của nó.</li>
 * </ul>
 *
 * <h2>Quản lý trạng thái và đồng thời lạc quan</h2>
 * <p>
 * Aggregate sử dụng cơ chế <b>optimistic concurrency control</b> thông qua
 * trường {@code version}. Mọi lệnh ghi (gồm {@link #tombstone(long, java.time.Instant)})
 * đều kiểm tra phiên bản mong đợi; nếu phiên bản thực tế khác sẽ ném ra
 * {@link com.familya.platform.error.OptimisticConcurrencyException} để bảo vệ
 * tính nhất quán trong môi trường nhiều tiến trình ghi đồng thời.
 * </p>
 *
 * <h2>Vòng đời</h2>
 * <p>
 * Một quan hệ được tạo ra ở trạng thái "sống" (chưa bị xóa mềm) với
 * {@code tombstonedAt == null}. Khi người dùng hoặc saga muốn xóa quan hệ, hệ
 * thống gọi {@link #tombstone(long, java.time.Instant)} để đánh dấu xóa mềm
 * mà không xóa vật lý dòng dữ liệu. Điều này giúp khôi phục được quan hệ
 * thông qua {@link com.familya.relationship.application.port.out.RelationshipRepository#untombstone}.
 * </p>
 *
 * @param id            định danh duy nhất của quan hệ (UUID, không null)
 * @param treeId        định danh cây gia phả chứa quan hệ (UUID, không null)
 * @param kind          loại quan hệ (PARENT_CHILD / SPOUSE / ADOPTION, không null)
 * @param fromMemberId  định danh thành viên phía "nguồn" (UUID, không null)
 * @param toMemberId    định danh thành viên phía "đích" (UUID, không null)
 * @param metadataJson  chuỗi JSON chứa siêu dữ liệu mở rộng (có thể null)
 * @param revision      số hiệu chỉnh sửa lúc tạo (do {@code commandSeq})
 * @param createdAt     thời điểm tạo (Instant, không null)
 * @param tombstonedAt  thời điểm xóa mềm; {@code null} nếu đang sống
 * @param version       số phiên bản dùng cho optimistic concurrency control
 */
public final class Relationship {

    /** Định danh duy nhất của quan hệ, do hệ thống sinh ra. */
    private final UUID id;
    /** Định danh cây gia phả mà quan hệ này thuộc về. */
    private final UUID treeId;
    /** Loại quan hệ (cha-con / vợ-chồng / nhận nuôi). */
    private Kind kind;
    /** Thành viên phía "nguồn" của cạnh. */
    private UUID fromMemberId;
    /** Thành viên phía "đích" của cạnh. */
    private UUID toMemberId;
    /** Chuỗi JSON chứa metadata bổ sung (ví dụ: ghi chú, ngày cưới). */
    private String metadataJson;
    /** Số hiệu chỉnh sửa tại thời điểm tạo (phản ánh thứ tự lệnh theo cây). */
    private final long revision;
    /** Thời điểm tạo quan hệ (không thay đổi trong suốt vòng đời). */
    private final java.time.Instant createdAt;
    /** Thời điểm xóa mềm; {@code null} nếu quan hệ còn hiệu lực. */
    private java.time.Instant tombstonedAt;
    /** Phiên bản aggregate, dùng cho optimistic concurrency. */
    private long version;

    /**
     * Khởi tạo một aggregate {@code Relationship} mới hoặc tái dựng từ cơ sở dữ liệu.
     *
     * @param id            định danh duy nhất của quan hệ, không null
     * @param treeId        định danh cây gia phả, không null
     * @param kind          loại quan hệ, không null
     * @param fromMemberId  thành viên phía nguồn, không null
     * @param toMemberId    thành viên phía đích, không null
     * @param metadataJson  chuỗi JSON metadata bổ sung, có thể null
     * @param revision      số hiệu chỉnh sửa lúc tạo
     * @param createdAt     thời điểm tạo, không null
     * @param tombstonedAt  thời điểm xóa mềm, có thể null nếu đang sống
     * @param version       phiên bản aggregate (cho optimistic locking)
     * @throws NullPointerException nếu bất kỳ tham số bắt buộc nào là null
     */
    public Relationship(UUID id, UUID treeId, Kind kind, UUID fromMemberId, UUID toMemberId,
                        String metadataJson, long revision, java.time.Instant createdAt,
                        java.time.Instant tombstonedAt, long version) {
        // Đảm bảo các trường bắt buộc luôn có giá trị - sớm phát hiện lỗi dữ liệu.
        this.id = Objects.requireNonNull(id, "id");
        this.treeId = Objects.requireNonNull(treeId, "treeId");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.fromMemberId = Objects.requireNonNull(fromMemberId, "fromMemberId");
        this.toMemberId = Objects.requireNonNull(toMemberId, "toMemberId");
        // metadataJson được phép null vì không phải quan hệ nào cũng có ghi chú.
        this.metadataJson = metadataJson;
        this.revision = revision;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        // tombstonedAt: null = còn sống, khác null = đã bị xóa mềm.
        this.tombstonedAt = tombstonedAt;
        this.version = version;
    }

    /** @return định danh duy nhất của quan hệ. */
    public UUID id() { return id; }

    /** @return định danh cây gia phả chứa quan hệ. */
    public UUID treeId() { return treeId; }

    /** @return loại quan hệ (PARENT_CHILD / SPOUSE / ADOPTION). */
    public Kind kind() { return kind; }

    /** @return định danh thành viên phía nguồn của cạnh. */
    public UUID fromMemberId() { return fromMemberId; }

    /** @return định danh thành viên phía đích của cạnh. */
    public UUID toMemberId() { return toMemberId; }

    /** @return chuỗi JSON metadata bổ sung (có thể null). */
    public String metadataJson() { return metadataJson; }

    /** @return số hiệu chỉnh sửa lúc tạo. */
    public long revision() { return revision; }

    /** @return thời điểm tạo quan hệ. */
    public java.time.Instant createdAt() { return createdAt; }

    /** @return thời điểm xóa mềm hoặc {@code null} nếu quan hệ còn sống. */
    public java.time.Instant tombstonedAt() { return tombstonedAt; }

    /** @return phiên bản hiện tại của aggregate (cho optimistic locking). */
    public long version() { return version; }

    /**
     * Kiểm tra quan hệ đã bị xóa mềm hay chưa.
     *
     * @return {@code true} nếu {@code tombstonedAt != null}, ngược lại {@code false}
     */
    public boolean isTombstoned() { return tombstonedAt != null; }

    /**
     * Đánh dấu quan hệ ở trạng thái "đã xóa mềm" (soft delete).
     * <p>
     * Quy trình thực hiện:
     * </p>
     * <ol>
     *   <li>Kiểm tra phiên bản hiện tại có khớp với {@code expectedVersion} hay không
     *       (optimistic concurrency control).</li>
     *   <li>Nếu quan hệ đã bị xóa mềm trước đó, phương thức trở thành phép toán
     *       idempotent và trả về ngay mà không thay đổi gì.</li>
     *   <li>Ghi nhận thời điểm xóa mềm và tăng phiên bản lên 1.</li>
     * </ol>
     *
     * @param expectedVersion phiên bản aggregate mà client tin là hiện hành
     * @param now             thời điểm hiện tại (do {@code Clock} tiêm vào để dễ test)
     * @throws com.familya.platform.error.OptimisticConcurrencyException
     *         nếu {@code version} hiện tại khác {@code expectedVersion}
     */
    public void tombstone(long expectedVersion, java.time.Instant now) {
        // Bước 1: kiểm tra phiên bản để chống ghi đè lẫn nhau giữa các tiến trình đồng thời.
        requireVersion(expectedVersion);
        // Bước 2: nếu đã bị xóa mềm rồi thì không làm gì thêm - đảm bảo idempotent.
        if (tombstonedAt != null) return;
        // Bước 3: ghi nhận thời điểm xóa mềm và tăng version (version + 1 là bắt buộc).
        this.tombstonedAt = now;
        this.version = this.version + 1;
    }

    /**
     * Yêu cầu phiên bản aggregate phải khớp với giá trị mong đợi.
     * <p>
     * Đây là cơ chế optimistic concurrency: nếu một tiến trình khác đã thay đổi
     * quan hệ này từ lần đọc cuối của client thì lệnh ghi sẽ bị từ chối thay vì
     * âm thầm ghi đè lên dữ liệu mới hơn.
     * </p>
     *
     * @param expected phiên bản mà client cho rằng đang hiện hành
     * @throws com.familya.platform.error.OptimisticConcurrencyException
     *         nếu phiên bản thực tế không khớp với phiên bản mong đợi
     */
    private void requireVersion(long expected) {
        if (this.version != expected) {
            // Thông báo lỗi chi tiết để phục vụ việc truy vết nguyên nhân xung đột.
            throw new com.familya.platform.error.OptimisticConcurrencyException(
                    "Relationship " + id + " version " + this.version + " != expected " + expected);
        }
    }

    /**
     * Liệt kê các loại quan hệ được hỗ trợ trong hệ thống.
     * <p>
     * Việc sử dụng {@code enum} giúp đảm bảo type-safety khi thao tác với các
     * loại quan hệ, đồng thời cho phép JPA/jOOQ sinh schema rõ ràng.
     * </p>
     */
    public enum Kind {
        /** Quan hệ cha-mẹ ↔ con (hướng từ tổ tiên đến hậu duệ). */
        PARENT_CHILD,
        /** Quan hệ vợ chồng (được lưu trữ đối xứng thành hai dòng). */
        SPOUSE,
        /** Quan hệ nhận nuôi (không thay đổi thế hệ của đứa trẻ). */
        ADOPTION
    }
}