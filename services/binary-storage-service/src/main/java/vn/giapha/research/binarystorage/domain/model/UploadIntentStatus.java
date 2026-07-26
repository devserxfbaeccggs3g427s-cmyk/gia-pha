package vn.giapha.research.binarystorage.domain.model;

/**
 * Upload intent lifecycle (Task 15.1, ck_upload_intents_status). The intent
 * tracks the handshake; the media object itself carries the content lifecycle
 * (PENDING_UPLOAD/PENDING_SCAN/ACTIVE/... on media_objects.status).
 *
 * <pre>
 * PENDING_UPLOAD ──browser PUT + verified──▶ UPLOADED ──promotion──▶ PROMOTED
 *        │                                       │
 *        ├── expires_at passes ──▶ EXPIRED       └── validation/scan veto ──▶ FAILED
 *        └── caller abort ──▶ CANCELLED
 * </pre>
 */
public enum UploadIntentStatus {
    /** Capability issued; waiting for the browser to PUT into quarantine. */
    PENDING_UPLOAD,
    /** Object observed in quarantine and independently verified; awaiting scan/promotion. */
    UPLOADED,
    /** Promoted to the final path and activated — terminal success. */
    PROMOTED,
    /** Never completed before {@code expires_at}; quarantine object is reaped. */
    EXPIRED,
    /** Verification, validation or scanning vetoed the object — terminal. */
    FAILED,
    /** Explicitly aborted by the requesting flow — terminal. */
    CANCELLED
}
