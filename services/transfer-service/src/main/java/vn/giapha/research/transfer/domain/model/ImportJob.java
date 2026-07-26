package vn.giapha.research.transfer.domain.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Single import-job row (Task 29). */
public record ImportJob(
        long importJobKey,
        String externalId,
        String treeExternalId,
        Long userKey,
        ImportFormat inputFormat,
        byte[] inputChecksum,
        long inputBytes,
        ImportMode mode,
        ImportDuplicateStrategy duplicateStrategy,
        ImportJobStatus status,
        int totalCount,
        int acceptedCount,
        int skippedCount,
        int errorCount,
        List<Map<String, Object>> errors,
        Instant completedAt,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public ImportJob {
        externalId = externalId == null ? "" : externalId;
        errors = errors == null ? List.of()
                : List.copyOf(errors.stream()
                        .map(err -> err == null ? Map.<String, Object>of()
                                : new LinkedHashMap<>(err))
                        .toList());
    }

    public enum ImportMode {
        PREVIEW,
        EXECUTE
    }
}
