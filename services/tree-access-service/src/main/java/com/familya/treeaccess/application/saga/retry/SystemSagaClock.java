package com.familya.treeaccess.application.saga.retry;

import java.time.Instant;

public final class SystemSagaClock implements SagaClock {
    @Override
    public Instant now() {
        return Instant.now();
    }
}
