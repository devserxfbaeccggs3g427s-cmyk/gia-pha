package com.familya.media.domain.model;

import java.time.Instant;
import java.util.UUID;

public record MediaAsset(
        UUID id,
        UUID treeId,
        UUID albumId,
        UUID ownerUserId,
        Kind kind,
        String mimeType,
        long byteSize,
        String sha256,
        String originalFilename,
        Status status,
        String quarantinePath,
        boolean promoted,
        Instant retentionHoldUntil,
        Instant tombstonedAt,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public enum Kind { PHOTO, VIDEO, AUDIO, DOCUMENT, OTHER }
    public enum Status { QUARANTINED, SCANNING, READY, FAILED, TOMBSTONED }
}
