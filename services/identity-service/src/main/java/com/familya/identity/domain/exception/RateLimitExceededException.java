package com.familya.identity.domain.exception;

import com.familya.platform.error.DomainException;
import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends DomainException {
    public RateLimitExceededException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, "rate.limit.exceeded", message);
    }
}
