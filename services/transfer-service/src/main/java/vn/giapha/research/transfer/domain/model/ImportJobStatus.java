package vn.giapha.research.transfer.domain.model;

/** Allowed import-job status (mirrors {@code ck_import_jobs_status}). */
public enum ImportJobStatus {
    PENDING,
    VALIDATING,
    PREVIEWED,
    APPLYING,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean terminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
