package com.familya.platform.error;

import org.springframework.http.HttpStatus;

public class StaleProjectionException extends DomainException {
    public StaleProjectionException(String message) { super(HttpStatus.CONFLICT, "projection.stale", message); }
}
