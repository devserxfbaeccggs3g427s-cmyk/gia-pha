package com.familya.sharing.application.port.out;

import java.util.*;

/**
 * Cổng (port) lưu trữ watermark và projection công khai allowlist, sử dụng
 * cho luồng tái tạo projection trong sharing-service.
 * <p>
 * Đây là một giao diện đơn giản hơn {@link AllowlistedProjectionRepository},
 * tập trung vào thao tác watermark và projection dạng phẳng (flat map).
 * Được {@code JdbcAllowlistedProjectionRepository} (trong package
 * {@code adapter.out.persistence}) hiện thực.
 */
public interface ShareWatermarkRepository {

    /**
     * Đọc watermark hiện tại cho một cây.
     *
     * @param treeId định danh cây gia phả.
     * @return giá trị watermark, hoặc {@code 0} nếu chưa có bản ghi.
     */
    long watermark(UUID treeId);

    /**
     * Cập nhật watermark kèm phiên bản.
     *
     * @param treeId    định danh cây gia phả.
     * @param watermark giá trị watermark mới.
     * @param version   phiên bản tương ứng.
     */
    void update(UUID treeId, long watermark, long version);

    /**
     * Đọc một projection cho một phạm vi/mục tiêu cụ thể.
     *
     * @param treeId   định danh cây gia phả.
     * @param scope    phạm vi của projection.
     * @param targetId định danh mục tiêu (có thể {@code null} với {@code TREE}).
     * @return {@link Optional} chứa {@code Map} các trường projection, hoặc rỗng nếu không có.
     */
    Optional<Map<String, Object>> projection(UUID treeId, com.familya.sharing.domain.model.ShareLink.Scope scope, UUID targetId);

    /**
     * Lưu một projection kèm watermark.
     *
     * @param treeId    định danh cây gia phả.
     * @param scope     phạm vi của projection.
     * @param targetId  định danh mục tiêu.
     * @param value     {@code Map} các trường của projection.
     * @param watermark phiên bản watermark.
     */
    void saveProjection(UUID treeId, com.familya.sharing.domain.model.ShareLink.Scope scope, UUID targetId, Map<String, Object> value, long watermark);
}