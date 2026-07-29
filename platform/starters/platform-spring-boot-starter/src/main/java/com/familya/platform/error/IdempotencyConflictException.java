package com.familya.platform.error;

import org.springframework.http.HttpStatus;

public class IdempotencyConflictException extends DomainException {
    public IdempotencyConflictException(String message) { super(HttpStatus.CONFLICT, "idempotency.conflict", message); }
}
