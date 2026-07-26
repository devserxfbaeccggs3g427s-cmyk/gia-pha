package vn.giapha.research.binarystorage.domain.model;

import java.time.Instant;

/** One row of {@code binary_replicas} (Task 35). */
public record BinaryReplicaRecord(
        long binaryReplicaKey,
        Long mediaKey,
        String primaryObjectPath,
        byte[] primarySha256,
        String archiveObjectPath,
        byte[] archiveSha256,
        ReplicaStatus status,
        int attempts,
        String lastError,
        Instant replicatedAt,
        Instant createdAt,
        Instant updatedAt) {}
