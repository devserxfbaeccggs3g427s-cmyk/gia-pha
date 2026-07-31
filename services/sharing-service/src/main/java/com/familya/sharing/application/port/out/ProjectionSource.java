package com.familya.sharing.application.port.out;

import java.util.*;

/**
 * Cổng (port) trừu tượng đại diện cho một nguồn projection mà
 * {@code RebuildPublicProjectionUseCase} có thể truy vấn để tái tạo các
 * projection công khai đã được lọc trắng.
 * <p>
 * Hiện tại cổng này được sử dụng như một điểm mở rộng: các adapter cụ thể có
 * thể cài đặt {@code rebuild(...)} để đọc dữ liệu từ nguồn khác nhau (ví dụ:
 * các bảng projection hiện có) và trả về một {@code Map} các trường công khai.
 *
 * @author gia-pha platform team
 */
public interface ProjectionSource {
    /**
     * Tái tạo (rebuild) một projection công khai cho một phạm vi/mục tiêu cụ thể.
     *
     * @param treeId   định danh cây gia phả.
     * @param scope    phạm vi chia sẻ cần tái tạo.
     * @param targetId định danh mục tiêu trong phạm vi (có thể {@code null} với {@code TREE}).
     * @param watermark phiên bản watermark mong muốn.
     * @return {@code Map} chứa các trường công khai đã được tái tạo.
     */
    Map<String, Object> rebuild(UUID treeId, com.familya.sharing.domain.model.ShareLink.Scope scope, UUID targetId, long watermark);
}