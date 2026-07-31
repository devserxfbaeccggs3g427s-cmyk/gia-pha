package com.familya.member.application.port.in;

import java.util.UUID;

/**
 * Đầu vào cho use case tombstone trực tiếp một thành viên (sử dụng trong một số ngữ cảnh
 * nội bộ; thường thì {@link com.familya.member.application.usecase.DeleteMemberSagaService}
 * mới là cách chuẩn để xóa thành viên).
 *
 * @param memberId            mã thành viên cần tombstone
 * @param actingUser          người dùng thực hiện
 * @param expectedVersion     phiên bản kỳ vọng (optimistic concurrency)
 * @param expectedTreeRevision phiên bản cây kỳ vọng
 */
public record TombstoneMemberCommand(UUID memberId, UUID actingUser, long expectedVersion, long expectedTreeRevision) { }