package com.familya.platform.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.Map;

/**
 * Stable error envelope. {@code code} is a machine-stable identifier;
 * {@code message} is human-readable and may be localized. {@code traceId}
 * is the OpenTelemetry trace id and is always present for production
 * support. {@code details} is an arbitrary allowlisted map.
 */
@JsonInclude(Include.NON_NULL)
public record ErrorResponse(
        String code,
        String message,
        String traceId,
        Map<String, Object> details
) {
    public static ErrorResponse of(String code, String message, String traceId) {
        return new ErrorResponse(code, message, traceId, null);
    }
}
