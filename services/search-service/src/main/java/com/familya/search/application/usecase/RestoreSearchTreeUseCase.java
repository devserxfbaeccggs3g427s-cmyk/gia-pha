package com.familya.search.application.usecase;

import com.familya.search.application.port.in.RestoreSearchTreeCommand;
import com.familya.search.application.port.out.SearchWatermarkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Restores search projections after a delete-tree Saga failure. The
 * projections themselves are read-only views that get re-populated by the
 * domain consumers on the next event; this use case simply reverses the
 * watermark advance so reads will not silently hide tree-scoped documents.
 */
@Service
public class RestoreSearchTreeUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(RestoreSearchTreeUseCase.class);

    private final SearchWatermarkRepository watermarks;

    public RestoreSearchTreeUseCase(SearchWatermarkRepository watermarks) {
        this.watermarks = watermarks;
    }

    @Transactional
    public Result execute(RestoreSearchTreeCommand cmd) {
        // We do not retain a per-operation snapshot, so the safest revert is
        // to refresh the watermark to "now - 1s" which forces the next read
        // to rehydrate from the source-of-truth events.
        watermarks.refreshForRestore(cmd.treeId(), Instant.now());
        LOG.info("Refreshed search watermark for tree {} operationId={}",
                cmd.treeId(), cmd.operationId());
        return new Result(0, 0L, 0L);
    }

    public record Result(int affectedCount, long appliedAggregateVersion, long appliedEpoch) { }
}