package com.familya.relationship.application.port.in;

import com.familya.relationship.domain.model.Relationship;

import java.util.UUID;

/**
 * Lệnh (command) yêu cầu tạo một quan hệ gia phả mới.
 * <p>
 * Đây là một {@code record} (value object bất biến) đại diện cho ý định của
 * client, không chứa bất kỳ logic nghiệp vụ nào. Việc xử lý lệnh được đảm
 * nhận bởi use case {@code CreateRelationshipUseCase}.
 * </p>
 *
 * <h2>Các bước xử lý tương ứng trong use case</h2>
 * <ol>
 *   <li>Kiểm tra quyền của {@link #actingUser} trên {@link #treeId} thông qua
 *       projection phân quyền.</li>
 *   <li>Đảm bảo cả {@link #fromMemberId} và {@link #toMemberId} đều tồn tại
 *       và chưa bị tombstone.</li>
 *   <li>Đảm bảo chưa tồn tại cạnh trùng {@code (treeId, kind, from, to)}.</li>
 *   <li>Đảm bảo cạnh mới không tạo vòng (chỉ áp dụng cho {@code PARENT_CHILD}).</li>
 *   <li>Chèn quan hệ vào DB, sinh sự kiện {@code RelationshipCreated} qua outbox.</li>
 * </ol>
 *
 * @param treeId                định danh cây gia phả sẽ chứa quan hệ mới
 * @param actingUser            định danh người dùng thực hiện lệnh (để kiểm tra quyền)
 * @param kind                  loại quan hệ (PARENT_CHILD / SPOUSE / ADOPTION)
 * @param fromMemberId          thành viên phía nguồn của cạnh
 * @param toMemberId            thành viên phía đích của cạnh
 * @param metadataJson          chuỗi JSON metadata bổ sung (có thể null)
 * @param expectedTreeRevision  phiên bản projection phân quyền mà client tin
 *                              là hiện hành (dùng cho optimistic check)
 */
public record CreateRelationshipCommand(
        UUID treeId,
        UUID actingUser,
        Relationship.Kind kind,
        UUID fromMemberId,
        UUID toMemberId,
        String metadataJson,
        long expectedTreeRevision
) { }