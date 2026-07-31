package com.familya.sharing.application.port.out;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Cổng (port) ra phía hạ tầng lưu trữ các projection công khai đã được lọc
 * trắng (allowlisted) cho sharing-service.
 * <p>
 * Mỗi projection chỉ chứa những trường được phép hiển thị công khai &mdash;
 * mọi khoá ngoài danh sách cho phép sẽ bị từ chối tại tầng ghi. Các phương
 * thức của cổng này được {@code JdbcAllowlistedProjectionRepository} hiện thực
 * bằng JDBC để thao tác với các bảng {@code share_*_projection} trong cơ sở
 * dữ liệu.
 * <p>
 * Cổng này được sử dụng cả bởi luồng tái tạo projection ({@code RebuildPublicProjectionUseCase})
 * và luồng tra cứu công khai ({@code ResolvePublicProjectionUseCase}).
 */
public interface AllowlistedProjectionRepository {

    /**
     * Đọc watermark (mốc nước) đã được ghi nhận cho một miền dữ liệu của một cây.
     *
     * @param treeId định danh cây gia phả.
     * @param domain miền dữ liệu (ví dụ: {@code "member"}, {@code "media"}...).
     * @return giá trị watermark hiện tại, hoặc {@code 0} nếu chưa có bản ghi.
     */
    long readWatermark(UUID treeId, String domain);

    /**
     * Cập nhật watermark của một miền dữ liệu &mdash; chỉ tiến tới nếu giá trị
     * mới lớn hơn giá trị hiện tại (đảm bảo watermark không bao giờ "lùi").
     *
     * @param treeId    định danh cây gia phả.
     * @param domain    miền dữ liệu.
     * @param watermark giá trị watermark mới.
     * @param at        thời điểm cập nhật.
     * @return giá trị watermark đã được ghi nhận (giống tham số truyền vào).
     */
    long advanceWatermark(UUID treeId, String domain, long watermark, Instant at);

    /**
     * Lưu projection allowlist cho một thành viên của cây.
     *
     * @param treeId       định danh cây gia phả.
     * @param memberId     định danh thành viên.
     * @param allowlisted  tập các trường được phép công khai.
     * @param tombstoned   {@code true} nếu bản ghi đã bị "xóa mềm" (tombstone).
     * @param at           thời điểm ghi nhận.
     */
    void saveMember(UUID treeId, UUID memberId, Map<String, Object> allowlisted, boolean tombstoned, Instant at);

    /**
     * Lưu projection allowlist cho một media của cây.
     *
     * @param treeId      định danh cây gia phả.
     * @param mediaId     định danh media.
     * @param allowlisted tập các trường được phép công khai.
     * @param tombstoned  {@code true} nếu bản ghi đã bị "xóa mềm".
     * @param at          thời điểm ghi nhận.
     */
    void saveMedia(UUID treeId, UUID mediaId, Map<String, Object> allowlisted, boolean tombstoned, Instant at);

    /**
     * Lưu projection allowlist cho một sự kiện của cây.
     *
     * @param treeId      định danh cây gia phả.
     * @param eventId     định danh sự kiện.
     * @param allowlisted tập các trường được phép công khai.
     * @param tombstoned  {@code true} nếu bản ghi đã bị "xóa mềm".
     * @param at          thời điểm ghi nhận.
     */
    void saveEvent(UUID treeId, UUID eventId, Map<String, Object> allowlisted, boolean tombstoned, Instant at);

    /**
     * Lưu projection allowlist cho một quan hệ (relationship) của cây.
     *
     * @param treeId     định danh cây gia phả.
     * @param relId      định danh quan hệ.
     * @param allowlisted tập các trường được phép công khai.
     * @param tombstoned  {@code true} nếu bản ghi đã bị "xóa mềm".
     * @param at          thời điểm ghi nhận.
     */
    void saveRelationship(UUID treeId, UUID relId, Map<String, Object> allowlisted, boolean tombstoned, Instant at);

    /**
     * Lưu projection allowlist cho cả cây gia phả.
     *
     * @param treeId      định danh cây gia phả.
     * @param allowlisted tập các trường được phép công khai.
     * @param tombstoned  {@code true} nếu cây đã bị "xóa mềm".
     * @param at          thời điểm ghi nhận.
     */
    void saveTree(UUID treeId, Map<String, Object> allowlisted, boolean tombstoned, Instant at);

    /**
     * Lưu projection công khai (allowlisted public projection) cho một phạm vi/mục tiêu
     * cụ thể kèm watermark.
     *
     * @param treeId   định danh cây gia phả.
     * @param scope    phạm vi của projection.
     * @param targetId định danh mục tiêu (có thể {@code null} với phạm vi {@code TREE}).
     * @param value    tập các trường được phép công khai.
     * @param watermark watermark tương ứng với phiên bản projection.
     * @param at       thời điểm ghi nhận.
     */
    void savePublicProjection(UUID treeId, ShareScope scope, UUID targetId, Map<String, Object> value, long watermark, Instant at);

    /**
     * Đọc projection công khai (allowlisted public projection) đã lưu.
     *
     * @param treeId   định danh cây gia phả.
     * @param scope    phạm vi của projection.
     * @param targetId định danh mục tiêu (có thể {@code null} với phạm vi {@code TREE}).
     * @return {@code Map} các trường công khai hoặc {@link Map#of()} nếu chưa có bản ghi.
     */
    Map<String, Object> readPublicProjection(UUID treeId, ShareScope scope, UUID targetId);

    /**
     * Liệt kê các phạm vi projection công khai mà sharing-service hỗ trợ.
     */
    enum ShareScope { TREE, MEMBER, MEDIA, EVENT }
}