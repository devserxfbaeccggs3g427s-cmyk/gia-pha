package com.familya.search.application.port.out;

import com.familya.search.domain.model.RevisionBarrier;
import com.familya.search.domain.model.Watermark;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SearchWatermarkRepository {

    Optional<Watermark> find(UUID treeId, Watermark.Domain domain);

    RevisionBarrier barrierFor(UUID treeId);

    void advance(UUID treeId, Watermark.Domain domain, long value);

    /**
     * Advance every per-domain watermark for the tree to the supplied
     * aggregate version/epoch. Default implementation is a no-op so
     * existing services stay binary-compatible.
     */
    default void advance(UUID treeId, long aggregateVersion, long epoch, Instant now) {
        for (Watermark.Domain d : Watermark.Domain.values()) {
            advance(treeId, d, aggregateVersion);
        }
    }

    /**
     * Force every per-domain watermark for the tree to a fresh value so
     * the next read repopulates projections from the source-of-truth event
     * stream. Used by {@code RestoreSearchTreeUseCase}. Default no-op.
     */
    default void refreshForRestore(UUID treeId, Instant now) { /* no-op */ }
}