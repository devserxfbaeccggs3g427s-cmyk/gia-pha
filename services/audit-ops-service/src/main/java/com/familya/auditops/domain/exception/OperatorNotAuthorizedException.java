package com.familya.auditops.domain.exception;

import com.familya.platform.error.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Operator attempted an action that requires the admin role, or
 * attempted to act on an operation that does not belong to them.
 */
public class OperatorNotAuthorizedException extends DomainException {
    public OperatorNotAuthorizedException(String message) {
        super(HttpStatus.FORBIDDEN, "operator.unauthorized", message);
    }
}