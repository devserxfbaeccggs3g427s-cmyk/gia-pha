package com.familya.treeaccess.application.usecase;

import com.familya.treeaccess.application.port.out.TreeRepository;
import com.familya.treeaccess.domain.model.AuthorizationProjection;
import com.familya.treeaccess.domain.model.TreeMembership;
import com.familya.platform.error.StaleProjectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Authorization use case. Answers "can this user do X on this tree?"
 * for callers inside the service. Other services consume the
 * {@code tree.memberships.v1} stream into their own projections; when
 * their local projection is missing or stale they fall back to a
 * deadline-bound gRPC call (see {@code TreeAccessLookupService}).
 *
 * <p>Staleness policy: the local projection row's {@code revision}
 * must be equal to or greater than the caller's expected revision. If
 * the row is absent or its {@code lastUpdatedAt} is older than the
 * freshness threshold (default 60 s for unsafe mutations), the unsafe
 * mutation fails closed with {@link StaleProjectionException}.</p>
 */
@Service
public class AuthorizeUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(AuthorizeUseCase.class);

    private final TreeRepository repo;
    private final Duration freshnessThreshold;

    public AuthorizeUseCase(TreeRepository repo,
                            @org.springframework.beans.factory.annotation.Value(
                                    "${familya.treeauth.freshness-threshold-seconds:60}") long thresholdSeconds) {
        this.repo = repo;
        this.freshnessThreshold = Duration.ofSeconds(thresholdSeconds);
    }

    /**
     * Returns the effective role (or empty) for the principal on the tree.
     * The owner is always ADMIN regardless of the membership row state,
     * satisfying the owner-immutability invariant.
     */
    public Optional<AuthorizationProjection> authorize(UUID treeId, UUID userId, long expectedRevision) {
        // Owner shortcut — owner always has ADMIN; never revoked.
        var treeOpt = repo.findTree(treeId);
        if (treeOpt.isPresent() && treeOpt.get().ownerUserId().equals(userId)) {
            return Optional.of(new AuthorizationProjection(
                    treeId, userId, TreeMembership.Role.ADMIN,
                    treeOpt.get().revision(), treeOpt.get().epoch(),
                    treeOpt.get().createdAt(), false, null, Instant.now()));
        }
        Optional<AuthorizationProjection> proj = repo.findProjection(treeId, userId);
        if (proj.isEmpty()) {
            LOG.debug("No projection row tree={} user={}", treeId, userId);
            return Optional.empty();
        }
        AuthorizationProjection p = proj.get();
        if (p.revoked()) {
            return Optional.empty();
        }
        if (p.revision() < expectedRevision) {
            // Stale-by-revision: caller asked for at least this
            // revision; we have an older snapshot.
            throw new StaleProjectionException(
                    "Projection revision " + p.revision() + " < expected " + expectedRevision);
        }
        Instant age = Duration.between(p.lastUpdatedAt(), Instant.now());
        if (age.compareTo(freshnessThreshold) > 0 && p.role() != null && p.role().canEdit()) {
            // Stale-by-freshness: only unsafe mutations must fail closed.
            // We surface stale for edit roles and let the caller decide.
            throw new StaleProjectionException(
                    "Projection stale by " + age.toSeconds() + "s for tree=" + treeId + " user=" + userId);
        }
        return Optional.of(p);
    }

    public boolean canEdit(UUID treeId, UUID userId, long expectedRevision) {
        return authorize(treeId, userId, expectedRevision)
                .map(AuthorizationProjection::canEdit)
                .orElse(false);
    }

    public boolean canView(UUID treeId, UUID userId, long expectedRevision) {
        return authorize(treeId, userId, expectedRevision)
                .map(AuthorizationProjection::canView)
                .orElse(false);
    }
}