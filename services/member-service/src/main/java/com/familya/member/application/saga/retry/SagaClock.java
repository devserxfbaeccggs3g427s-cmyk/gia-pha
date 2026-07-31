package com.familya.member.application.saga.retry;

import java.time.Instant;

public interface SagaClock {
    Instant now();
}
