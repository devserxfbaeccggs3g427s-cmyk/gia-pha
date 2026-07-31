package com.familya.sharing.application.port.out;

import com.familya.sharing.domain.model.ShareLink;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cổng (port) truy cập kho lưu trữ các liên kết chia sẻ (share links).
 * <p>
 * Đây là hợp đồng trừu tượng cho phép tầng ứng dụng thao tác với bảng
 * {@code share_link} mà không phụ thuộc vào công nghệ lưu trữ cụ thể. Hiện
 * được {@code JdbcShareLinkRepository} hiện thực bằng JDBC.
 */
public interface ShareLinkRepository {

    /**
     * Thêm mới một liên kết chia sẻ vào kho lưu trữ.
     *
     * @param link đối tượng {@link ShareLink} cần thêm.
     */
    void insert(ShareLink link);

    /**
     * Cập nhật một liên kết chia sẻ đã có (thường dùng khi thu hồi).
     *
     * @param link đối tượng {@link ShareLink} chứa thông tin cập nhật.
     */
    void update(ShareLink link);

    /**
     * Tra cứu một liên kết chia sẻ theo định danh.
     *
     * @param id định danh của liên kết.
     * @return {@link Optional} chứa liên kết nếu tồn tại.
     */
    Optional<ShareLink> findById(UUID id);

    /**
     * Tra cứu một liên kết chia sẻ theo giá trị băm của token.
     *
     * @param hash chuỗi hash SHA-256 hex của token.
     * @return {@link Optional} chứa liên kết nếu tồn tại.
     */
    Optional<ShareLink> findByTokenHash(String hash);

    /**
     * Liệt kê tất cả các liên kết chia sẻ thuộc về một cây gia phả.
     *
     * @param treeId định danh cây gia phả.
     * @return danh sách liên kết (rỗng nếu không có).
     */
    List<ShareLink> listByTree(UUID treeId);

    /**
     * Liệt kê các liên kết đang hoạt động (chưa bị thu hồi) thuộc một phạm vi
     * và mục tiêu cụ thể trong cây gia phả.
     *
     * @param treeId   định danh cây gia phả.
     * @param scope    phạm vi chia sẻ.
     * @param targetId định danh mục tiêu (có thể {@code null} với {@code TREE}).
     * @return danh sách liên kết đang hoạt động.
     */
    List<ShareLink> listActiveByScope(UUID treeId, ShareLink.Scope scope, UUID targetId);
}