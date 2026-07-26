package vn.giapha.research.binarystorage.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Malware scan verdict (Task 15.5). {@code ERROR} — scanner unreachable,
 * overloaded or crashed — is a first-class outcome so the pipeline can fail
 * closed: nothing with an {@code ERROR} verdict is ever activated.
 */
public record ScanOutcome(Result result, String engine, String signatureVersion, Instant scannedAt) {

    public enum Result { CLEAN, INFECTED, ERROR }

    public ScanOutcome {
        Objects.requireNonNull(result, "result");
    }

    public boolean clean() {
        return result == Result.CLEAN;
    }
}
