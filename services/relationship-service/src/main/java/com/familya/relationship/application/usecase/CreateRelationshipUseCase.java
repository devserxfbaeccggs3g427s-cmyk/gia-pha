package com.familya.relationship.application.usecase;

import com.familya.relationship.application.port.in.CreateRelationshipCommand;
import com.familya.relationship.application.port.out.AuthorizationProjectionRepository;
import com.familya.relationship.application.port.out.MemberExistenceProjection;
import com.familya.relationship.application.port.out.RelationshipEventPublisher;
import com.familya.relationship.application.port.out.RelationshipRepository;
import com.familya.relationship.domain.event.RelationshipCreated;
import com.familya.relationship.domain.exception.DanglingMemberReferenceException;
import com.familya.relationship.domain.exception.DuplicateRelationshipException;
import com.familya.relationship.domain.graph.GraphAlgorithms;
import com.familya.relationship.domain.model.Relationship;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.error.StaleProjectionException;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Creates a graph edge. The use case:
 *
 * <ol>
 *   <li>Authorises from the local membership projection (deny on stale
 *       or absent row).</li>
 *   <li>Validates the proposed edges against the Member existence
 *       projection (refuse dangling references).</li>
 *   <li>Serialises the command by treeId via
 *       {@link RelationshipRepository#nextCommandSeq} (per-tree
 *       monotonic counter inside {@code FOR UPDATE}).</li>
 *   <li>Checks the canonical logical key uniqueness.</li>
 *   <li>Checks the cycle invariant (PARENT_CHILD only).</li>
 *   <li>Persists the relationship + appends the command-log row +
 *       stages the outbox event.</li>
 * </ol>
 *
 * <p>The same transaction commits all three writes so the projection
 * rebuild and the emergency reconciliation can rely on the
 * relationship row and the command-log row being in lockstep.</p>
 */
@Service
public class CreateRelationshipUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(CreateRelationshipUseCase.class);

    private final RelationshipRepository repo;
    private final RelationshipEventPublisher publisher;
    private final MemberExistenceProjection memberProj;
    private final AuthorizationProjectionRepository authRepo;
    private final PlatformMetrics metrics;
    private final Clock clock;

    public CreateRelationshipUseCase(RelationshipRepository repo, RelationshipEventPublisher publisher,
                                     MemberExistenceProjection memberProj,
                                     AuthorizationProjectionRepository authRepo,
                                     PlatformMetrics metrics, Clock clock) {
        this.repo = repo;
        this.publisher = publisher;
        this.memberProj = memberProj;
        this.authRepo = authRepo;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public UUID execute(CreateRelationshipCommand cmd) {
        metrics.mutationAccepted("relationship-service", "createRelationship");
        // Authorisation — must be editor or admin.
        var auth = authRepo.findAuth(cmd.treeId(), cmd.actingUser())
                .orElseThrow(() -> new ForbiddenException(
                        "No authorization projection for tree=" + cmd.treeId() + " user=" + cmd.actingUser()));
        if (auth.isRevoked() || !auth.canEdit()) {
            throw new ForbiddenException("User " + cmd.actingUser() + " cannot edit tree " + cmd.treeId());
        }
        if (auth.revision() < cmd.expectedTreeRevision()) {
            throw new StaleProjectionException(
                    "Auth projection revision " + auth.revision() + " < expected " + cmd.expectedTreeRevision());
        }

        // Member existence — refuse dangling references.
        if (!memberProj.isAvailable(cmd.treeId(), cmd.fromMemberId())) {
            throw new DanglingMemberReferenceException(
                    "fromMemberId " + cmd.fromMemberId() + " is missing or tombstoned");
        }
        if (!memberProj.isAvailable(cmd.treeId(), cmd.toMemberId())) {
            throw new DanglingMemberReferenceException(
                    "toMemberId " + cmd.toMemberId() + " is missing or tombstoned");
        }

        // Per-tree serializer — advance the command counter.
        long seq = repo.nextCommandSeq(cmd.treeId());

        // Canonical-key uniqueness.
        if (repo.existsEdge(cmd.treeId(), cmd.kind(), cmd.fromMemberId(), cmd.toMemberId())) {
            throw new DuplicateRelationshipException(
                    "Duplicate " + cmd.kind() + " edge " + cmd.fromMemberId() + " -> " + cmd.toMemberId());
        }

        // Cycle invariant.
        if (cmd.kind() == Relationship.Kind.PARENT_CHILD) {
            var existing = repo.listByTree(cmd.treeId(), false);
            GraphAlgorithms.assertNoCycle(existing, cmd.fromMemberId(), cmd.toMemberId());
        }

        Instant now = clock.now();
        UUID id = UUID.randomUUID();
        Relationship rel = new Relationship(id, cmd.treeId(), cmd.kind(),
                cmd.fromMemberId(), cmd.toMemberId(), cmd.metadataJson(),
                seq, now, null, 0L);
        repo.insert(rel);
        // Spouse edges are stored symmetrically; insert the mirror row.
        if (cmd.kind() == Relationship.Kind.SPOUSE) {
            UUID mirrorId = UUID.randomUUID();
            Relationship mirror = new Relationship(mirrorId, cmd.treeId(), Relationship.Kind.SPOUSE,
                    cmd.toMemberId(), cmd.fromMemberId(), cmd.metadataJson(),
                    seq, now, null, 0L);
            repo.insert(mirror);
        }
        repo.appendCommandLog(cmd.treeId(), seq, "create_relationship",
                cmd.actingUser(), payloadHash(cmd), now);
        publisher.publish(new RelationshipCreated(id, cmd.treeId(), cmd.kind(),
                cmd.fromMemberId(), cmd.toMemberId(), cmd.metadataJson(), seq, now));
        if (cmd.kind() == Relationship.Kind.SPOUSE) {
            publisher.publish(new RelationshipCreated(UUID.randomUUID(), cmd.treeId(),
                    Relationship.Kind.SPOUSE, cmd.toMemberId(), cmd.fromMemberId(),
                    cmd.metadataJson(), seq, now));
        }
        LOG.info("Created relationship id={} kind={} {} -> {} seq={} actingUser={}",
                id, cmd.kind(), cmd.fromMemberId(), cmd.toMemberId(), seq, cmd.actingUser());
        return id;
    }

    private static String payloadHash(CreateRelationshipCommand cmd) {
        String s = cmd.treeId() + "|" + cmd.kind() + "|" + cmd.fromMemberId() + "|" + cmd.toMemberId();
        return Integer.toHexString(s.hashCode());
    }

    public interface Clock { Instant now(); }
}