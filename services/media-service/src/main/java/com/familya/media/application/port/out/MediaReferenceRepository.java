package com.familya.media.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MediaReferenceRepository {
    void upsert(UUID mediaId, UUID treeId, String targetKind, UUID targetId, String status, Instant at, String errorCode);

    void clear(UUID mediaId, String targetKind, UUID targetId);

    List<ReferenceRow> listForMedia(UUID mediaId);

    List<ReferenceRow> listByTarget(UUID treeId, String targetKind, UUID targetId);

    /** Persist compensation snapshot for the delete-member Saga. Default no-op. */
    default void saveCompensationSnapshot(UUID operationId, String snapshotJson) { /* no-op */ }

    /**
     * Restore MEMBER-kind references that were cleared by the detach step.
     * Default returns 0 so the interface stays binary-compatible.
     */
    default int restoreMemberReferences(UUID operationId) { return 0; }

    /** Bulk clear references whose target belongs to the tree. */
    default int bulkClearByTree(UUID treeId, java.util.List<String> targetKinds) {
        int n = 0;
        for (String kind : targetKinds) {
            for (ReferenceRow row : listByTarget(treeId, kind, null)) {
                clear(row.mediaId(), kind, row.targetId());
                n++;
            }
        }
        return n;
    }

    record ReferenceRow(UUID mediaId, UUID treeId, String targetKind, UUID targetId, String status, Instant lastAttemptAt, String lastErrorCode) { }
}