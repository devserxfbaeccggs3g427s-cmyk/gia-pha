package vn.giapha.research.binarystorage.domain.model;

/** Mirrors {@code ck_binary_replicas_status}. */
public enum ReplicaStatus {
    PENDING,
    REPLICATED,
    FAILED,
    ORPHANED
}
