package com.familya.treeaccess.application.saga.retry;

import java.time.Instant;

/**
 * Abstraction đồng hồ cho Saga, giúp dễ mock trong kiểm thử.
 */
public interface SagaClock {
    /**
     * @return thời điểm hiện tại theo đồng hồ đã cài đặt
     */
    Instant now();
}
