package vn.giapha.research.binarystorage.domain.error;

/**
 * Single failure taxonomy for binary storage operations (Task 14.3). Both the
 * control gateway and the signed data plane translate into these reasons, so
 * calling modules never branch on transport details — and the DoD status map
 * (401/403/404/409/412/429/5xx/timeout/expiry) lives in exactly one place per
 * adapter. Messages never contain URLs, pathnames or credentials.
 */
public class BinaryStorageException extends RuntimeException {

    /** Stable failure classes; see the adapter status-mapping tables. */
    public enum Reason {
        /** Our service signature was rejected (401) — configuration/rotation defect. */
        UNAUTHORIZED,
        /** Authenticated but outside policy, e.g. path prefix (403 from gateway). */
        FORBIDDEN,
        /** A signed data-plane capability was rejected or has expired (401/403 from store). */
        CAPABILITY_EXPIRED,
        /** Object or route absent (404). */
        NOT_FOUND,
        /** No-overwrite/promotion race (409). */
        CONFLICT,
        /** Conditional request failed, e.g. ETag mismatch on delete (412). */
        PRECONDITION_FAILED,
        /** Upstream throttling (429); caller decides whether to back off and retry. */
        RATE_LIMITED,
        /** Store or gateway server failure (5xx). */
        UPSTREAM_ERROR,
        /** Connect/read timeout or connection failure. */
        TIMEOUT,
        /** Circuit breaker is open; the dependency is presumed down. */
        CIRCUIT_OPEN,
        /** Response violated the frozen contract (unparseable envelope, missing fields). */
        PROTOCOL_ERROR
    }

    private final Reason reason;

    public BinaryStorageException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public BinaryStorageException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    /**
     * Whether retrying an <em>idempotent</em> operation may help. Non-idempotent
     * operations are never retried regardless of this flag (Task 14.3 DoD).
     */
    public boolean retryable() {
        return reason == Reason.TIMEOUT || reason == Reason.UPSTREAM_ERROR;
    }
}
