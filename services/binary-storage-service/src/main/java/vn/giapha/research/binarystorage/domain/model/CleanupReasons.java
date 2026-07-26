package vn.giapha.research.binarystorage.domain.model;

/**
 * Stable reason codes recorded on {@code file_cleanup_jobs.reason}
 * (VARCHAR(60)). Free-form strings are allowed for future flows; these
 * constants cover the upload lifecycle so dashboards can group reliably.
 */
public final class CleanupReasons {

    /** Intent expired before the browser completed the upload (Task 15.7). */
    public static final String EXPIRED_QUARANTINE = "EXPIRED_QUARANTINE";
    /** Quarantine copy left behind after successful promotion (Task 15.6). */
    public static final String PROMOTED_QUARANTINE = "PROMOTED_QUARANTINE";
    /** Content verification (size/type/hash) rejected the upload (Task 15.3/15.4). */
    public static final String VALIDATION_REJECTED = "VALIDATION_REJECTED";
    /** Malware scanner returned INFECTED (Task 15.5). */
    public static final String MALWARE_REJECTED = "MALWARE_REJECTED";
    /** Quarantine object with no live intent found by the orphan sweep (Task 15.7). */
    public static final String ORPHAN_OBJECT = "ORPHAN_OBJECT";
    /** Media/artifact record was deleted; binary follows durably (Req 13.10). */
    public static final String RECORD_DELETED = "RECORD_DELETED";

    private CleanupReasons() {
    }
}
