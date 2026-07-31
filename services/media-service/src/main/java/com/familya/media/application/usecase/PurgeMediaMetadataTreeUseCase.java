package com.familya.media.application.usecase;

import com.familya.media.application.port.in.PurgeMediaMetadataTreeCommand;
import com.familya.media.application.port.out.MediaReferenceRepository;
import com.familya.media.application.port.out.MediaRepository;
import com.familya.media.application.port.out.MediaRetentionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Participant step for delete-tree Saga. Hides all tree-scoped media
 * metadata, clears all references that point at tree-scoped targets, and
 * places a retention hold on every media row so the delayed binary cleanup
 * worker will not delete binaries until the configured grace period expires.
 *
 * <p>Physical binary deletion is NOT performed here; it is the responsibility
 * of the delayed cleanup worker after the hold expires.
 */
@Service
public class PurgeMediaMetadataTreeUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(PurgeMediaMetadataTreeUseCase.class);

    static final Duration RETENTION_GRACE = Duration.ofDays(30);

    private final MediaRepository media;
    private final MediaReferenceRepository refs;
    private final MediaRetentionRepository retention;

    public PurgeMediaMetadataTreeUseCase(MediaRepository media,
                                         MediaReferenceRepository refs,
                                         MediaRetentionRepository retention) {
        this.media = media;
        this.refs = refs;
        this.retention = retention;
    }

    @Transactional
    public Result execute(PurgeMediaMetadataTreeCommand cmd) {
        Instant now = Instant.now();
        int tombstoned = media.bulkTombstoneByTree(cmd.treeId(), now);
        if (cmd.placeRetentionHolds()) {
            retention.bulkPlaceHoldByTree(cmd.treeId(), now.plus(RETENTION_GRACE),
                    "delete-tree-saga:" + cmd.operationId());
        }
        int cleared = refs.bulkClearByTree(cmd.treeId(), List.of("MEMBER", "EVENT"));
        LOG.info("Bulk-tombstoned {} media rows and cleared {} references on tree {} operationId={}",
                tombstoned, cleared, cmd.treeId(), cmd.operationId());
        return new Result(tombstoned, Math.max(0L, cmd.targetAggregateVersion()), cmd.targetEpoch());
    }

    public record Result(int affectedCount, long appliedAggregateVersion, long appliedEpoch) { }
}