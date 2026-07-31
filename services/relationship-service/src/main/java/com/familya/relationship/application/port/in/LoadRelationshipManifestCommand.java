package com.familya.relationship.application.port.in;

import com.familya.relationship.domain.model.Relationship;

import java.util.List;
import java.util.UUID;

/**
 * Lệnh yêu cầu nạp (load) một manifest các quan hệ gia phả vào hệ thống.
 * <p>
 * Manifest thường được sử dụng trong quá trình <b>migration dữ liệu</b> từ hệ
 * thống cũ hoặc từ bản sao lưu. Use case {@code LoadRelationshipManifestUseCase}
 * xử lý từng dòng trong manifest, bỏ qua các quan hệ đã tồn tại (idempotent)
 * hoặc ghi nhận các bản ghi trùng lặp (duplicate) để thống kê.
 * </p>
 *
 * <h2>Các trường quan trọng</h2>
 * <ul>
 *   <li>{@link #relationships}: danh sách các dòng quan hệ cần nạp.</li>
 *   <li>{@link #replaySafe}: khi {@code true}, các dòng trùng ID được bỏ qua
 *       thay vì coi là lỗi (chế độ an toàn khi chạy lại).</li>
 * </ul>
 *
 * @param treeId       định danh cây gia phả đích
 * @param relationships danh sách các dòng quan hệ cần nạp
 * @param replaySafe   cờ chế độ "chạy lại an toàn"
 */
public record LoadRelationshipManifestCommand(
        UUID treeId,
        List<RelationshipLine> relationships,
        boolean replaySafe
) {
    /**
     * Đại diện một dòng trong manifest - mô tả một quan hệ cần được tái tạo.
     *
     * @param relationshipId định danh quan hệ (UUID)
     * @param kind           loại quan hệ
     * @param fromMemberId   thành viên phía nguồn
     * @param toMemberId     thành viên phía đích
     * @param metadataJson   chuỗi JSON metadata
     * @param revision       số hiệu chỉnh sửa ban đầu
     * @param createdAt      thời điểm tạo trong hệ thống nguồn
     */
    public record RelationshipLine(
            UUID relationshipId,
            Relationship.Kind kind,
            UUID fromMemberId,
            UUID toMemberId,
            String metadataJson,
            long revision,
            java.time.Instant createdAt
    ) { }
}