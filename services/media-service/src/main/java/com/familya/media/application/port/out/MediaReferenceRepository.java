package com.familya.media.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MediaReferenceRepository {
    void upsert(UUID mediaId, UUID treeId, String targetKind, UUID targetId, String status, Instant at, String errorCode);

    void clear(UUID mediaId, String targetKind, UUID targetId);

    List<ReferenceRow> listForMedia(UUID mediaId);

    List<ReferenceRow> listByTarget(UUID treeId, String targetKind, UUID targetId);

    record ReferenceRow(UUID mediaId, UUID treeId, String targetKind, UUID targetId, String status, Instant lastAttemptAt, String lastErrorCode) { }
}
