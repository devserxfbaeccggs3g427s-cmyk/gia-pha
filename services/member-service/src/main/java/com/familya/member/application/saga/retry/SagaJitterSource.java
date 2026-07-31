package com.familya.member.application.saga.retry;

/**
 * Hợp đồng trừu tượng cho nguồn jitter — tách riêng để có thể thay thế bằng nguồn xác
 * định trong kiểm thử.
 */
public interface SagaJitterSource {
    /**
     * Sinh jitter có giới hạn trong [0, baseMs * percent / 100].
     *
     * @param baseMs  giá trị cơ sở (ms)
     * @param percent phần trăm của cơ sở làm biên
     * @return jitter (ms)
     */
    long boundedJitter(long baseMs, int percent);
}
