package com.familya.platform.error;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends DomainException {
    public ForbiddenException(String message) { super(HttpStatus.FORBIDDEN, "forbidden", message); }
}
