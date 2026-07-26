package vn.giapha.research.binarystorage.domain.model;

import java.time.Duration;
import java.util.Objects;

import vn.giapha.research.binarystorage.shared.error.ValidationException;

/**
 * Parameters for one signed URL capability. Constraints are enforced again by
 * the gateway (defense in depth); this record only rejects requests that are
 * structurally impossible so misuse fails in-process instead of round-tripping.
 *
 * @param operation       exactly one data-plane operation
 * @param pathname        exact object pathname (server-generated, Req 7.4)
 * @param ttl             optional; gateway clamps to its configured maximum
 * @param contentType     PUT only, required — single allowed content type
 * @param maxSizeBytes    PUT only, optional — narrows the gateway upload cap
 * @param callbackPayload PUT only, optional — opaque correlation value (e.g.
 *                        upload-intent id) echoed in the completion callback
 * @param useCache        GET only — allow cached reads for immutable objects
 * @param ifMatch         DELETE only, optional — conditional delete ETag
 */
public record SignedUrlRequest(
        BlobOperation operation,
        String pathname,
        Duration ttl,
        String contentType,
        Long maxSizeBytes,
        String callbackPayload,
        boolean useCache,
        String ifMatch) {

    public SignedUrlRequest {
        Objects.requireNonNull(operation, "operation");
        if (pathname == null || pathname.isBlank()) {
            throw new ValidationException("pathname is required");
        }
        if (operation == BlobOperation.PUT && (contentType == null || contentType.isBlank())) {
            throw new ValidationException("contentType is required for put capabilities");
        }
        if (maxSizeBytes != null && maxSizeBytes <= 0) {
            throw new ValidationException("maxSizeBytes must be positive");
        }
    }

    public static SignedUrlRequest forGet(String pathname, Duration ttl) {
        return new SignedUrlRequest(BlobOperation.GET, pathname, ttl, null, null, null, false, null);
    }

    public static SignedUrlRequest forHead(String pathname, Duration ttl) {
        return new SignedUrlRequest(BlobOperation.HEAD, pathname, ttl, null, null, null, false, null);
    }

    public static SignedUrlRequest forPut(
            String pathname, Duration ttl, String contentType, Long maxSizeBytes, String callbackPayload) {
        return new SignedUrlRequest(
                BlobOperation.PUT, pathname, ttl, contentType, maxSizeBytes, callbackPayload, false, null);
    }

    public static SignedUrlRequest forDelete(String pathname, Duration ttl, String ifMatch) {
        return new SignedUrlRequest(BlobOperation.DELETE, pathname, ttl, null, null, null, false, ifMatch);
    }
}
