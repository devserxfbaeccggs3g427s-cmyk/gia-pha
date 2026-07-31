package com.familya.sharing.application.usecase;

import com.familya.sharing.application.port.out.AllowlistedProjectionRepository;
import com.familya.sharing.application.port.out.AllowlistedProjectionRepository.ShareScope;
import com.familya.sharing.application.port.out.ShareChangePublisher;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Rebuilds an allowlisted projection from the per-domain sources.
 * Only the fields allowed by the strict allowlist are kept; unknown
 * keys are rejected by the writer.
 */
@Service
public class RebuildPublicProjectionUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(RebuildPublicProjectionUseCase.class);

    private final AllowlistedProjectionRepository projections;
    private final ShareChangePublisher publisher;
    private final PlatformMetrics metrics;

    public RebuildPublicProjectionUseCase(AllowlistedProjectionRepository projections,
                                           ShareChangePublisher publisher,
                                           PlatformMetrics metrics) {
        this.projections = projections;
        this.publisher = publisher;
        this.metrics = metrics;
    }

    @Transactional
    public long execute(UUID treeId, ShareScope scope, UUID targetId, long newWatermark) {
        Map<String, Object> value = switch (scope) {
            case TREE -> projections.readPublicProjection(treeId, ShareScope.TREE, targetId);
            case MEMBER -> projections.readPublicProjection(treeId, ShareScope.MEMBER, targetId);
            case MEDIA -> projections.readPublicProjection(treeId, ShareScope.MEDIA, targetId);
            case EVENT -> projections.readPublicProjection(treeId, ShareScope.EVENT, targetId);
        };
        if (value == null) {
            value = Map.of();
        }
        Instant now = Instant.now();
        projections.savePublicProjection(treeId, scope, targetId, value, newWatermark, now);
        projections.advanceWatermark(treeId, scope.name().toLowerCase(), newWatermark, now);
        publisher.projectionRebuilt(treeId, newWatermark);
        metrics.mutationAccepted("sharing-service", "rebuildProjection");
        LOG.info("Rebuilt projection tree={} scope={} target={} watermark={}", treeId, scope, targetId, newWatermark);
        return newWatermark;
    }
}
