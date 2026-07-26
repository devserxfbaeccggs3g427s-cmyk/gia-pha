package vn.giapha.research.identity.domain.cutover;

import java.time.Instant;

/**
 * Global identity authority state (Task 20.3, ADR-009). A single row in
 * {@code identity_authority} decides which writer is allowed to mutate
 * identity data; both producers and consumers check this row before every
 * write so a structural two-writer window is impossible.
 */
public record IdentityAuthority(
        IdentityWriter writer,
        boolean freeze,
        boolean legacyReadsAllowed,
        boolean legacyWritesAllowed,
        boolean springReadsAllowed,
        boolean springWritesAllowed,
        Instant lastSwitchAt,
        String lastSwitchReason,
        long version) {

    public boolean canWrite(IdentityWriter writer) {
        if (freeze) {
            return false;
        }
        return switch (writer) {
            case LEGACY -> legacyWritesAllowed;
            case SPRING -> springWritesAllowed;
            case TRANSITION -> legacyWritesAllowed || springWritesAllowed;
        };
    }

    public boolean canRead(IdentityWriter writer) {
        return switch (writer) {
            case LEGACY -> legacyReadsAllowed;
            case SPRING -> springReadsAllowed;
            case TRANSITION -> legacyReadsAllowed || springReadsAllowed;
        };
    }
}
