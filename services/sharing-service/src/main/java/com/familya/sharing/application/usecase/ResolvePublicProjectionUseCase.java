package com.familya.sharing.application.usecase;

import com.familya.sharing.application.port.in.PublicLookupQuery;
import com.familya.sharing.application.port.out.AllowlistedProjectionRepository;
import com.familya.sharing.application.port.out.AllowlistedProjectionRepository.ShareScope;
import com.familya.sharing.application.port.out.ShareLinkRepository;
import com.familya.sharing.domain.exception.ShareNotFoundException;
import com.familya.sharing.domain.model.ShareLink;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Synchronously resolves a public projection for a {@code token}
 * plus {@code mediaId}. Revoked, expired, and unknown tokens never
 * succeed. The response only carries the allowlisted fields — any
 * unknown key in the projection is rejected at write time.
 */
@Service
public class ResolvePublicProjectionUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(ResolvePublicProjectionUseCase.class);

    private final ShareLinkRepository links;
    private final AllowlistedProjectionRepository projections;
    private final PlatformMetrics metrics;

    public ResolvePublicProjectionUseCase(ShareLinkRepository links, AllowlistedProjectionRepository projections,
                                          PlatformMetrics metrics) {
        this.links = links;
        this.projections = projections;
        this.metrics = metrics;
    }

    @Transactional(readOnly = true)
    public Result execute(PublicLookupQuery q) {
        String hash = CreateShareLinkUseCase.sha256Hex(q.token());
        ShareLink link = links.findByTokenHash(hash)
                .orElseThrow(() -> new ShareNotFoundException("share.unknown"));
        if (link.revokedAt() != null) {
            metrics.mutationFailed("sharing-service", "publicLookup", "share.revoked");
            throw new ShareNotFoundException("share.revoked");
        }
        if (link.expiresAt() != null && link.expiresAt().isBefore(q.now())) {
            metrics.mutationFailed("sharing-service", "publicLookup", "share.expired");
            throw new ShareNotFoundException("share.expired");
        }
        if (link.scope() == ShareLink.Scope.MEDIA && q.mediaId() != null) {
            ShareScope scope = ShareScope.MEDIA;
            Map<String, Object> projection = projections.readPublicProjection(link.treeId(), scope, q.mediaId());
            if (projection == null || projection.isEmpty()) {
                throw new ShareNotFoundException("media.unavailable");
            }
            return new Result(link.id(), link.scope(), link.role(), projection);
        }
        Map<String, Object> projection = projections.readPublicProjection(link.treeId(),
                ShareScope.valueOf(link.scope().name()), link.targetId());
        if (projection == null) {
            throw new ShareNotFoundException("scope.unavailable");
        }
        LOG.info("Public lookup shareId={} tree={} scope={}", link.id(), link.treeId(), link.scope());
        return new Result(link.id(), link.scope(), link.role(), projection);
    }

    public record Result(UUID shareId, ShareLink.Scope scope, ShareLink.Role role, Map<String, Object> projection) { }
}
