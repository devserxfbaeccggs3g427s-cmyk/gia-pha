package com.familya.auditops.domain.exception;

import com.familya.platform.error.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Orchestrator determined the Saga cannot complete without operator
 * intervention. Maps to HTTP 409 with code {@code saga.manual_review}.
 */
public class ManualReviewRequiredException extends DomainException {
    public ManualReviewRequiredException(String message) {
        super(HttpStatus.CONFLICT, "saga.manual_review", message);
    }
}