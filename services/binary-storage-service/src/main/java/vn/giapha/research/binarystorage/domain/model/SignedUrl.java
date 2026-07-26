package vn.giapha.research.binarystorage.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A single-operation, exact-path, short-expiry capability issued by the blob
 * control gateway (Req 7.5-7.7). The URL is a credential: it must never be
 * logged or persisted — callers hand it to the browser (uploads/downloads) or
 * use it immediately for a server-side data-plane call.
 */
public record SignedUrl(
        String url,
        String pathname,
        BlobOperation operation,
        Instant expiresAt) {

    public SignedUrl {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(pathname, "pathname");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    /** Redacted by design: renders without the URL credential. */
    @Override
    public String toString() {
        return "SignedUrl[operation=" + operation + ", expiresAt=" + expiresAt + "]";
    }
}
