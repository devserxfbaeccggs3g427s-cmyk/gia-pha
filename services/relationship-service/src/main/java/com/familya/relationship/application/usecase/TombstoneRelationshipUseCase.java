package com.familya.relationship.application.usecase;

import com.familya.relationship.application.port.in.TombstoneRelationshipCommand;
import com.familya.relationship.application.port.out.AuthorizationProjectionRepository;
import com.familya.relationship.application.port.out.RelationshipEventPublisher;
import com.familya.relationship.application.port.out.RelationshipRepository;
import com.familya.relationship.domain.event.RelationshipTombstoned;
import com.familya.relationship.domain.exception.RelationshipNotFoundException;
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

@Service
public class TombstoneRelationshipUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(TombstoneRelationshipUseCase.class);

    private final RelationshipRepository repo;
    private final RelationshipEventPublisher publisher;
    private final AuthorizationProjectionRepository authRepo;
    private final PlatformMetrics metrics;
    private final Clock clock;

    public TombstoneRelationshipUseCase(RelationshipRepository repo, RelationshipEventPublisher publisher,
                                         AuthorizationProjectionRepository authRepo,
                                         PlatformMetrics metrics, Clock clock) {
        this.repo = repo;
        this.publisher = publisher;
        this.authRepo = authRepo;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public void execute(TombstoneRelationshipCommand cmd) {
        metrics.mutationAccepted("relationship-service", "tombstoneRelationship");
        Relationship rel = repo.findById(cmd.relationshipId())
                .orElseThrow(() -> new RelationshipNotFoundException("Relationship " + cmd.relationshipId() + " not found"));
        var auth = authRepo.findAuth(rel.treeId(), cmd.actingUser())
                .orElseThrow(() -> new ForbiddenException(
                        "No authorization projection for tree=" + rel.treeId() + " user=" + cmd.actingUser()));
        if (auth.isRevoked() || !auth.canEdit()) {
            throw new ForbiddenException("User " + cmd.actingUser() + " cannot edit tree " + rel.treeId());
        }
        if (auth.revision() < cmd.expectedTreeRevision()) {
            throw new StaleProjectionException(
                    "Auth projection revision " + auth.revision() + " < expected " + cmd.expectedTreeRevision());
        }
        Instant now = clock.now();
        rel.tombstone(cmd.expectedVersion(), now);
        repo.update(rel);
        long seq = repo.nextCommandSeq(rel.treeId());
        repo.appendCommandLog(rel.treeId(), seq, "tombstone_relationship",
                cmd.actingUser(), "", now);
        publisher.publish(new RelationshipTombstoned(rel.id(), rel.treeId(), seq, now));
        LOG.info("Tombstoned relationship id={} seq={} actingUser={}", rel.id(), seq, cmd.actingUser());
    }

    public interface Clock { Instant now(); }
}