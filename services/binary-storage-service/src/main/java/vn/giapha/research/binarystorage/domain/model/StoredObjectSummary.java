package vn.giapha.research.binarystorage.domain.model;

import java.time.Instant;

/**
 * One entry of a gateway listing. Carries metadata only — the gateway never
 * returns store URLs, so listings cannot be turned into access.
 */
public record StoredObjectSummary(String pathname, long sizeBytes, Instant uploadedAt) {
}
