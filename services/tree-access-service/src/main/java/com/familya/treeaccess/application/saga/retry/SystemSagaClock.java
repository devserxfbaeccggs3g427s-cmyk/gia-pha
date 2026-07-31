package com.familya.treeaccess.application.saga.retry;

import java.time.Instant;

/**
 * Cài đặt {@link SagaClock} sử dụng {@link Instant#now()}.
 */
public final class SystemSagaClock implements SagaClock {
    /**
     * @return thời điểm hiện tại từ đồng hồ hệ thống
     */
    @Override
    public Instant now() {
        return Instant.now();
    }
}
