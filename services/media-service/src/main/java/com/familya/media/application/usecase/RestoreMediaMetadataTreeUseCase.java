package com.familya.media.application.usecase;

import com.familya.media.application.port.in.RestoreMediaMetadataTreeCommand;
import com.familya.media.application.port.out.MediaRepository;
import com.familya.media.application.port.out.MediaRetentionRepository;
import com.familya.media.domain.model.MediaAsset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class RestoreMediaMetadataTreeUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(RestoreMediaMetadataTreeUseCase.class);

    private final MediaRepository media;
    private final MediaRetentionRepository retention;

    public RestoreMediaMetadataTreeUseCase(MediaRepository media, MediaRetentionRepository retention) {
        this.media = media;
        this.retention = retention;
    }

    @Transactional
    public Result execute(RestoreMediaMetadataTreeCommand cmd) {
        List<MediaAsset> all = media.listByTree(cmd.treeId(), true);
        Instant now = Instant.now();
        long maxVersion = 0L;
        int restored = 0;
        for (MediaAsset asset : all) {
            if (asset.tombstonedAt() == null) continue;
            // Release any retention hold placed by the purge step so the
            // cleanup worker does not delete binaries.
            retention.releaseHoldForOperation(asset.id(), cmd.operationId());
            MediaAsset alive = new MediaAsset(
                    asset.id(), asset.treeId(), asset.albumId(), asset.ownerUserId(),
                    asset.kind(), asset.mimeType(), asset.byteSize(), asset.sha256(),
                    asset.originalFilename(), asset.status(), asset.quarantinePath(),
                    asset.promoted(), asset.retentionHoldUntil(), null,
                    asset.createdAt(), now, asset.version() + 1);
            media.update(alive);
            maxVersion = Math.max(maxVersion, alive.version());
            restored++;
        }
        LOG.info("Restored {} media metadata rows on tree {} operationId={}",
                restored, cmd.treeId(), cmd.operationId());
        return new Result(restored, maxVersion, 0L);
    }

    public record Result(int affectedCount, long appliedAggregateVersion, long appliedEpoch) { }
}