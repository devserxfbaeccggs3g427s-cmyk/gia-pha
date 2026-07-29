package com.familya.sharing.application.usecase;

import com.familya.sharing.application.port.in.RevokeShareLinkCommand;
import com.familya.sharing.application.port.out.ShareAuthorization;
import com.familya.sharing.application.port.out.ShareChangePublisher;
import com.familya.sharing.application.port.out.ShareLinkRepository;
import com.familya.sharing.domain.exception.ShareNotFoundException;
import com.familya.sharing.domain.model.ShareLink;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.error.OptimisticConcurrencyException;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class RevokeShareLinkUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(RevokeShareLinkUseCase.class);

    private final ShareLinkRepository repo;
    private final ShareAuthorization authz;
    private final ShareChangePublisher publisher;
    private final PlatformMetrics metrics;

    public RevokeShareLinkUseCase(ShareLinkRepository repo, ShareAuthorization authz,
                                  ShareChangePublisher publisher, PlatformMetrics metrics) {
        this.repo = repo;
        this.authz = authz;
        this.publisher = publisher;
        this.metrics = metrics;
    }

    @Transactional
    public void execute(RevokeShareLinkCommand cmd) {
        metrics.mutationAccepted("sharing-service", "revokeShareLink");
        ShareLink link = repo.findById(cmd.shareId())
                .orElseThrow(() -> new ShareNotFoundException("Share " + cmd.shareId() + " not found"));
        ShareAuthorization.Decision d = authz.authorize(link.treeId(), cmd.actingUser(), cmd.expectedTreeRevision());
        if (!d.isAllowed()) {
            throw new ForbiddenException("Cannot revoke share link: " + d.reason());
        }
        if (link.version() != cmd.expectedVersion()) {
            throw new OptimisticConcurrencyException(
                    "Share " + cmd.shareId() + " expected version " + cmd.expectedVersion() + " but found " + link.version());
        }
        Instant now = Instant.now();
        ShareLink revoked = new ShareLink(
                link.id(), link.treeId(), link.scope(), link.targetId(), link.role(),
                link.tokenHash(), link.createdByUserId(), link.createdAt(), link.expiresAt(),
                now, cmd.reason(), link.revision(), link.version() + 1);
        repo.update(revoked);
        publisher.shareLinkRevoked(revoked);
        LOG.info("Revoked share link id={} tree={} reason={}", cmd.shareId(), link.treeId(), cmd.reason());
    }
}
