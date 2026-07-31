package com.familya.treeaccess.application.saga.retry;

import java.time.Instant;

public interface SagaClock {
    Instant now();
}
