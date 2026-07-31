package com.familya.search.application.port.in;

import java.util.UUID;

/**
 * Truy vấn gợi ý (autocomplete) gửi từ tầng adapter vào use case
 * {@code AutocompleteUseCase}.
 *
 * <p>Chứa thông tin cần thiết để kiểm tra quyền truy cập và thực hiện truy
 * vấn gợi ý theo tiền tố trong phạm vi một cây gia phả.</p>
 *
 * @param treeId               định danh cây gia phả cần truy vấn.
 * @param actingUser           định danh người dùng đang thực hiện truy vấn.
 * @param expectedTreeRevision phiên bản cây mà client tin là mới nhất; nếu
 *                             projection còn cũ hơn, use case sẽ từ chối.
 * @param prefix               tiền tố người dùng đang gõ (sẽ được chuẩn hoá).
 * @param limit                số lượng gợi ý tối đa; nếu {@code null}, use
 *                             case sẽ áp dụng giá trị mặc định và giới hạn
 *                             trên an toàn.
 */
public record AutocompleteQuery(
        UUID treeId,
        UUID actingUser,
        long expectedTreeRevision,
        String prefix,
        Integer limit) {
}
