package com.familya.treeaccess.application.saga.retry;

/**
 * Nguồn jitter cho chính sách retry Saga: cho phép tách jitter khỏi {@link RandomSagaJitterSource}
 * để kiểm thử có thể điều khiển xác định.
 */
public interface SagaJitterSource {
    /**
     * Tính jitter bounded dựa trên {@code baseMs} và {@code percent}.
     *
     * @param baseMs  giá trị cơ sở (millisecond)
     * @param percent phần trăm (0-100)
     * @return giá trị jitter (millisecond)
     */
    long boundedJitter(long baseMs, int percent);
}
