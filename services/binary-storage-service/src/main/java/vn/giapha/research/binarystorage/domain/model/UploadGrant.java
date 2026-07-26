package vn.giapha.research.binarystorage.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Result of a successful upload request (Task 15.2): the persisted intent id
 * for correlation plus the single-use browser PUT capability. The frontend
 * uploads directly to the store — bytes never transit the backend.
 */
public record UploadGrant(
        String intentExternalId,
        SignedUrl uploadUrl,
        /** After this instant the intent is reaped and the capability is useless. */
        Instant intentExpiresAt) {

    public UploadGrant {
        Objects.requireNonNull(intentExternalId, "intentExternalId");
        Objects.requireNonNull(uploadUrl, "uploadUrl");
        Objects.requireNonNull(intentExpiresAt, "intentExpiresAt");
    }
}
