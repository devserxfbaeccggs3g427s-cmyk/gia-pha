package com.familya.platform.error;

import com.familya.platform.api.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Global error handler. Maps domain exceptions to the stable
 * {@link ErrorResponse} envelope. The {@code traceId} is sourced from
 * the {@code X-Trace-Id} header (set by the Gateway / platform
 * starter) or generated locally. Production stack traces are logged
 * but never returned in the response body.
 */
@RestControllerAdvice
public class GlobalErrorHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalErrorHandler.class);

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomain(DomainException ex, HttpServletRequest req) {
        return error(ex.getStatus(), ex.getCode(), ex.getMessage(), req, ex);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, Object> details = new HashMap<>();
        details.put("fields", ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(fe -> fe.getField(), fe -> fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage(), (a, b) -> a)));
        String traceId = traceId(req);
        LOG.warn("Validation failed traceId={} details={}", traceId, details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("validation.failed", "Request validation failed", traceId, details));
    }

    @ExceptionHandler(OptimisticConcurrencyException.class)
    public ResponseEntity<ErrorResponse> handleConcurrency(OptimisticConcurrencyException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "version.conflict", ex.getMessage(), req, ex);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ErrorResponse> handleIdempotency(IdempotencyConflictException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "idempotency.conflict", ex.getMessage(), req, ex);
    }

    @ExceptionHandler(StaleProjectionException.class)
    public ResponseEntity<ErrorResponse> handleStale(StaleProjectionException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "projection.stale", ex.getMessage(), req, ex);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, "not.found", ex.getMessage(), req, ex);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex, HttpServletRequest req) {
        return error(HttpStatus.FORBIDDEN, "forbidden", ex.getMessage(), req, ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAny(Exception ex, HttpServletRequest req) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "internal.error", "An unexpected error occurred", req, ex);
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message,
                                                       HttpServletRequest req, Throwable cause) {
        String traceId = traceId(req);
        if (status.is5xxServerError()) {
            LOG.error("Service error code={} traceId={}", code, traceId, cause);
        } else {
            LOG.warn("Client error code={} traceId={} message={}", code, traceId, message);
        }
        return ResponseEntity.status(status).body(new ErrorResponse(code, message, traceId, null));
    }

    private static String traceId(HttpServletRequest req) {
        String h = req.getHeader("X-Trace-Id");
        return h != null ? h : UUID.randomUUID().toString();
    }
}
