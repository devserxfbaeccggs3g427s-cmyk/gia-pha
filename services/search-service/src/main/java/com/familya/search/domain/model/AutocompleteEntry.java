package com.familya.search.domain.model;

import java.util.UUID;

/**
 * Bản ghi (record) đại diện cho một mục gợi ý (autocomplete) trong bảng tra cứu
 * nhanh. Mỗi mục ứng với một "bề mặt" hiển thị (surface) - ví dụ tên thành viên,
 * tiêu đề sự kiện, tên tệp media - kèm theo tiền tố đã chuẩn hoá để so khớp
 * nhanh khi người dùng gõ vào ô tìm kiếm.
 *
 * <p>Bản ghi này là dữ liệu chỉ-đọc, bất biến, được chiếu (project) vào cơ sở
 * dữ liệu bởi các consumer Kafka và được truy vấn bởi
 * {@code AutocompleteUseCase}.</p>
 *
 * @param treeId            định danh của cây gia phả mà mục này thuộc về; dùng
 *                          để phạm vi hoá kết quả theo từng cây.
 * @param ownerId           định danh thực thể sở hữu (member/event/media); có
 *                          thể {@code null} cho các mục không gắn với thực thể
 *                          cụ thể.
 * @param surface           chuỗi hiển thị gốc (giữ nguyên dấu, hoa/thường, khoảng
 *                          trắng) - chính là giá trị trả về cho client.
 * @param normalizedPrefix  tiền tố đã được {@code VietnameseNormalizer} chuẩn
 *                          hoá: bỏ dấu, đổi {@code đ/Đ} thành {@code d/D},
 *                          chữ thường, gọn khoảng trắng. Thường lấy 3 ký tự
 *                          đầu để phục vụ truy vấn LIKE.
 * @param weight            trọng số ưu tiên khi xếp hạng gợi ý; giá trị càng
 *                          lớn thì càng được ưu tiên hiển thị.
 */
public record AutocompleteEntry(
        UUID treeId,
        UUID ownerId,
        String surface,
        String normalizedPrefix,
        int weight
) { }
