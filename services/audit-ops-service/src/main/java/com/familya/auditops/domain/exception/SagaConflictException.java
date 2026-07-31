package com.familya.auditops.domain.exception;

import com.familya.platform.error.DomainException;
import org.springframework.http.HttpStatus;

/**
 * The Saga state machine refuses the proposed transition. Either the
 * transition is illegal or the participant reply targets a stale
 * revision. Maps to HTTP 409.
 */
public class SagaConflictException extends DomainException {
    public SagaConflictException(String message) {
        super(HttpStatus.CONFLICT, "saga.conflict", message);
    }
}