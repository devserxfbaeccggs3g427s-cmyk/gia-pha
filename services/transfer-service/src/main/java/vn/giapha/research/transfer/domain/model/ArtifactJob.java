package vn.giapha.research.transfer.domain.model;

import java.time.Instant;
import java.util.Map;

/** One row of {@code generated_artifact_jobs} (Task 31). */
public record ArtifactJob(
        long artifactJobKey,
        String externalId,
        String treeExternalId,
        long ownerUserKey,
        String operation,
        byte[] optionHash,
        Map<String, Object> options,
        ArtifactJobStatus status,
        int progressPercent,
        boolean cancelRequested,
        String resultObjectPath,
        byte[] resultChecksum,
        Long resultBytes,
        Instant expiresAt,
        String lastError,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
