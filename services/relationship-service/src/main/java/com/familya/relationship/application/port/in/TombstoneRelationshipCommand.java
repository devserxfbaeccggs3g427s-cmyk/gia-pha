package com.familya.relationship.application.port.in;

import java.util.UUID;

/**
 * Lệnh yêu cầu đánh dấu xóa mềm (tombstone) một quan hệ gia phả đã tồn tại.
 * <p>
 * Đây là một {@code record} (value object bất biến). Use case
 * {@code TombstoneRelationshipUseCase} sẽ thực hiện các bước:
 * </p>
 * <ol>
 *   <li>Tra cứu quan hệ theo {@link #relationshipId}; nếu không có thì ném
 *       {@code RelationshipNotFoundException}.</li>
 *   <li>Kiểm tra quyền của {@link #actingUser} trên cây chứa quan hệ.</li>
 *   <li>Đảm bảo {@code version} của quan hệ khớp với {@link #expectedVersion}
 *       (optimistic concurrency).</li>
 *   <li>Đánh dấu xóa mềm, cập nhật DB và sinh sự kiện
 *       {@code RelationshipTombstoned}.</li>
 * </ol>
 *
 * @param relationshipId         định danh quan hệ cần xóa
 * @param actingUser             người dùng thực hiện lệnh (dùng để kiểm tra quyền)
 * @param expectedVersion        phiên bản aggregate mà client tin là hiện hành
 * @param expectedTreeRevision   phiên bản projection phân quyền mà client cho
 *                               là đã được cập nhật
 */
public record TombstoneRelationshipCommand(
        UUID relationshipId,
        UUID actingUser,
        long expectedVersion,
        long expectedTreeRevision) { }