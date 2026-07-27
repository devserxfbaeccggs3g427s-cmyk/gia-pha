package com.familya.identity.domain.exception;

import com.familya.platform.error.DomainException;
import org.springframework.http.HttpStatus;

public class EmailAlreadyExistsException extends DomainException {
    public EmailAlreadyExistsException(String email) {
        super(HttpStatus.CONFLICT, "email.exists", "Email already registered: " + email);
    }
}
