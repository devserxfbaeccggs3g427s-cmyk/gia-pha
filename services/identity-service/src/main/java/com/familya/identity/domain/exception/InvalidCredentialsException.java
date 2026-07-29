package com.familya.identity.domain.exception;

import com.familya.platform.error.DomainException;
import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends DomainException {
    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "credentials.invalid", "Invalid email or password.");
    }
}
