package com.familya.member.application.port.in;

import java.util.UUID;

/**
 * Đầu vào cho use case gộp hai thành viên trong cùng một cây. Thành viên nguồn sẽ bị
 * tombstone sau khi gộp; thành viên survivor giữ lại và kế thừa các thuộc tính còn thiếu.
 *
 * @param survivorId            mã thành viên survivor (giữ lại)
 * @param sourceMemberId        mã thành viên nguồn sẽ bị gộp vào survivor
 * @param actingUser            người dùng thực hiện
 * @param expectedVersion       phiên bản kỳ vọng của survivor (optimistic concurrency)
 * @param expectedTreeRevision  phiên bản kỳ vọng của cây
 */
public record MergeMembersCommand(UUID survivorId, UUID sourceMemberId, UUID actingUser, long expectedVersion, long expectedTreeRevision) { }