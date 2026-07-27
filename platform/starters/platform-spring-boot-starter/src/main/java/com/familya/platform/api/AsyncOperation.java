package com.familya.platform.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Async operation envelope returned by every cross-service mutation
 * (ADR-007). The Gateway emits this on {@code 202 Accepted}. Clients poll
 * {@code GET /api/v2/operations/{operationId}} until the status is
 * terminal. Result, error, and watermarks are optional and only present
 * when the operation has progressed.
 */
public record AsyncOperation(
        UUID operationId,
        Status status,
        String statusUrl,
        Map<String, Object> result,
        ErrorBody error,
        Map<String, String> watermarks,
        Instant updatedAt
) {
    public enum Status {
        PENDING, RUNNING, SUCCEEDED, FAILED, COMPENSATING, COMPENSATED, MANUAL_REVIEW
    }

    public static AsyncOperation accepted(UUID id, String statusUrl) {
        return new AsyncOperation(id, Status.PENDING, statusUrl, null, null, null, Instant.now());
    }

    public record ErrorBody(String code, String message, String traceId, Map<String, Object> details) { }
}
