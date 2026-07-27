package com.familya.platform.error;

import org.springframework.http.HttpStatus;

public class OptimisticConcurrencyException extends DomainException {
    public OptimisticConcurrencyException(String message) { super(HttpStatus.CONFLICT, "version.conflict", message); }
}
