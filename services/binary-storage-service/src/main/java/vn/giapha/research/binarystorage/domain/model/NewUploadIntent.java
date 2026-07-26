package vn.giapha.research.binarystorage.domain.model;

import java.time.Instant;
import java.util.Objects;

import vn.giapha.research.binarystorage.shared.error.ValidationException;

/**
 * Creation payload for an upload intent. The caller (media/import flows)
 * supplies the server-generated paths and the constraints; the lifecycle
 * engine owns everything that happens afterwards.
 */
public record NewUploadIntent(
        String externalId,
        long treeKey,
        Long mediaKey,
        Long requestedByUserKey,
        String quarantineObjectPath,
        String finalObjectPath,
        String expectedMimeType,
        long expectedMaxBytes,
        byte[] expectedSha256,
        Instant expiresAt) {

    public NewUploadIntent {
        Objects.requireNonNull(externalId, "externalId");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (quarantineObjectPath == null || !quarantineObjectPath.startsWith("quarantine/")) {
            throw new ValidationException("quarantineObjectPath must sit under quarantine/");
        }
        if (finalObjectPath == null || finalObjectPath.isBlank()) {
            throw new ValidationException("finalObjectPath is required");
        }
        if (expectedMimeType == null || expectedMimeType.isBlank()) {
            throw new ValidationException("expectedMimeType is required");
        }
        if (expectedMaxBytes <= 0) {
            throw new ValidationException("expectedMaxBytes must be positive");
        }
    }
}
