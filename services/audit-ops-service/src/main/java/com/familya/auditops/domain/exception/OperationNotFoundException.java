package com.familya.auditops.domain.exception;

import com.familya.platform.error.NotFoundException;

/**
 * Operation does not exist. Maps to HTTP 404 via the platform
 * {@code GlobalErrorHandler}.
 */
public class OperationNotFoundException extends NotFoundException {
    public OperationNotFoundException(String operationId) {
        super("Operation " + operationId + " was not found.");
    }
}