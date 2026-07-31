package com.familya.search.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Tài liệu tìm kiếm cho một tệp media (ảnh, video, tài liệu) trong cây gia phả.
 *
 * <p>Bản chiếu này được duy trì bởi {@code SearchProjectionConsumer} khi nhận
 * sự kiện từ topic {@code media.events.v1} và được truy vấn bởi
 * {@code SearchMediaUseCase}. Trường {@code normalized_filename} được tính
 * sẵn bằng {@code VietnameseNormalizer} để phục vụ tìm kiếm không phân biệt
 * dấu.</p>
 *
 * @param treeId       định danh cây gia phả.
 * @param mediaId      định danh tệp media.
 * @param filename     tên tệp hiển thị (giữ nguyên dấu và khoảng trắng).
 * @param kind         phân loại media (ảnh, video, tài liệu...). Mặc định
 *                     {@code "OTHER"} nếu không rõ.
 * @param tombstoned   {@code true} nếu media đã bị tách khỏi cây (xoá mềm).
 * @param lastUpdated  thời điểm cập nhật cuối trong bảng chiếu.
 */
public record MediaSearchDocument(
        UUID treeId,
        UUID mediaId,
        String filename,
        String kind,
        boolean tombstoned,
        Instant lastUpdated
) { }
