package vn.giapha.research.media.domain;

import java.time.Instant;
import java.util.List;

/**
 * Media object aggregate. Binary bytes live in private Vercel Blob; this row
 * owns metadata, lifecycle state, scan evidence and replication state.
 * Associations (members, events, albums, avatars) are stored in dedicated
 * same-tree association tables.
 */
public record MediaObject(
        long mediaKey,
        long treeKey,
        String externalId,
        String filename,
        String originalName,
        String mimeType,
        long fileSize,
        String originalObjectPath,
        String originalEtag,
        byte[] originalSha256,
        String thumbnailObjectPath,
        String thumbnailEtag,
        byte[] thumbnailSha256,
        String caption,
        Instant takenAt,
        TakenAtPrecision takenAtPrecision,
        Long uploadedByUserKey,
        Instant uploadedAt,
        String scanEngine,
        ScanResult scanResult,
        String scanSignatureVersion,
        Instant scannedAt,
        ReplicationStatus replicationStatus,
        MediaStatus status,
        List<Long> memberKeys,
        Long albumKey,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public MediaObject {
        memberKeys = List.copyOf(memberKeys);
    }

    /** Precision of the legacy date-or-datetime `takenAt` value. */
    public enum TakenAtPrecision {
        DAY,
        SECOND
    }

    public enum ScanResult {
        CLEAN,
        INFECTED,
        ERROR
    }

    public enum ReplicationStatus {
        NONE,
        PENDING,
        REPLICATED,
        FAILED
    }
}
