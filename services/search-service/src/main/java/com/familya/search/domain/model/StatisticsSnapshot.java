package com.familya.search.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Bản chụp nhanh thống kê của một cây gia phả tại một mức watermark nhất định.
 *
 * <p>Được sinh ra bởi {@code ComputeStatisticsUseCase} bằng cách đếm trực tiếp
 * trên các bảng chiếu {@code search_member_doc}, {@code search_event_doc},
 * {@code search_media_doc} và {@code member_generation_projection}. Số liệu
 * này phản ánh trạng thái "hiện tại" của các tài liệu chưa bị xoá mềm
 * ({@code tombstoned = false}).</p>
 *
 * @param treeId       định danh cây gia phả.
 * @param memberCount  số thành viên còn hiệu lực (chưa tombstoned).
 * @param generations  số thế hệ phân biệt trong cây (tính từ cột
 *                     {@code member_generation_projection}).
 * @param eventsCount  số sự kiện còn hiệu lực.
 * @param mediaCount   số tệp media còn hiệu lực.
 * @param computedAt   thời điểm tính toán.
 * @param watermark    mức watermark mà bản thống kê phản ánh.
 */
public record StatisticsSnapshot(
        UUID treeId,
        long memberCount,
        int generations,
        long eventsCount,
        long mediaCount,
        Instant computedAt,
        long watermark
) { }
