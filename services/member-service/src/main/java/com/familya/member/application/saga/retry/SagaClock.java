package com.familya.member.application.saga.retry;

import java.time.Instant;

/**
 * Hợp đồng trừu tượng cho đồng hồ dùng trong Saga. Cho phép thay thế bằng đồng hồ giả
 * (fake clock) trong kiểm thử.
 */
public interface SagaClock {
    /**
     * Trả về thời điểm hiện tại.
     * @return {@link Instant} hiện tại theo nguồn đồng hồ đã cấu hình
     */
    Instant now();
}
