package com.familya.identity.domain.exception;

import com.familya.platform.error.DomainException;
import org.springframework.http.HttpStatus;

public class AccountLockedException extends DomainException {
    public AccountLockedException(String message) {
        super(HttpStatus.LOCKED, "account.locked", message);
    }
}
