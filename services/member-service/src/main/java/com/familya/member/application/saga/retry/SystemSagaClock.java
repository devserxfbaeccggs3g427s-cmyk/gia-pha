package com.familya.member.application.saga.retry;

import java.time.Instant;

/**
 * Triển khai {@link SagaClock} sử dụng {@link Instant#now()} của JVM. Đây là triển
 * khai mặc định trong môi trường sản xuất.
 */
public final class SystemSagaClock implements SagaClock {
    /**
     * Trả về thời điểm hiện tại của hệ thống.
     * @return {@link Instant#now()}
     */
    @Override
    public Instant now() {
        return Instant.now();
    }
}
