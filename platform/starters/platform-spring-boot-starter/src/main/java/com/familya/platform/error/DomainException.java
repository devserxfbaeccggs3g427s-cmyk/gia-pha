package com.familya.platform.error;

import org.springframework.http.HttpStatus;

/**
 * Base type for service-defined domain exceptions. The
 * {@link com.familya.platform.error.GlobalErrorHandler} maps subclasses
 * to HTTP responses using {@link #getStatus()} and {@link #getCode()}.
 */
public abstract class DomainException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected DomainException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    protected DomainException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
