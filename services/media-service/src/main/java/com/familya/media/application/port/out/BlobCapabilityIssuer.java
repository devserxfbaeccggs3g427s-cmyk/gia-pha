package com.familya.media.application.port.out;

import java.util.UUID;

/**
 * Blob capability issuer. Implementations must never log or persist
 * the resulting {@code signedPutUrl} / {@code signedGetUrl}; they are
 * bearer secrets returned once to the caller and discarded.
 */
public interface BlobCapabilityIssuer {

    Capability issue(UUID treeId, UUID mediaId, String exactPath, String mimeType);

    void invalidate(UUID mediaId);

    record Capability(String exactPath, String signedPutUrl, String signedGetUrl,
                      long expiresAtEpochMs) { }
}
