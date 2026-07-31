package com.familya.treeaccess.application.saga.retry;

public interface SagaJitterSource {
    long boundedJitter(long baseMs, int percent);
}
