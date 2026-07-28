package com.familya.member.application.usecase;

import com.familya.member.application.port.in.MergeMembersCommand;
import com.familya.member.application.port.out.MemberEventPublisher;
import com.familya.member.application.port.out.MemberRepository;
import com.familya.member.domain.event.MemberMerged;
import com.familya.member.domain.exception.MemberNotFoundException;
import com.familya.member.domain.model.CanonicalKey;
import com.familya.member.domain.model.Member;
import com.familya.member.domain.model.MemberAuthRow;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.projection.AuthorizationProjection;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class MergeMembersUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(MergeMembersUseCase.class);

    private final MemberRepository repo;
    private final MemberEventPublisher publisher;
    private final AuthorizationProjection authz;
    private final PlatformMetrics metrics;
    private final Clock clock;

    public MergeMembersUseCase(MemberRepository repo, MemberEventPublisher publisher,
                                AuthorizationProjection authz, PlatformMetrics metrics, Clock clock) {
        this.repo = repo;
        this.publisher = publisher;
        this.authz = authz;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public void execute(MergeMembersCommand cmd) {
        metrics.mutationAccepted("member-service", "mergeMembers");
        Member survivor = repo.findById(cmd.survivorId())
                .orElseThrow(() -> new MemberNotFoundException("Survivor " + cmd.survivorId() + " not found"));
        Member source = repo.findById(cmd.sourceMemberId())
                .orElseThrow(() -> new MemberNotFoundException("Source " + cmd.sourceMemberId() + " not found"));
        AuthorizationProjection.Decision<MemberAuthRow> decision =
                authz.authorize(survivor.treeId(), cmd.actingUser(), cmd.expectedTreeRevision(), MemberAuthRow.class);
        if (!decision.isAllowed()) {
            throw new ForbiddenException(
                    "User " + cmd.actingUser() + " cannot edit tree " + survivor.treeId()
                            + " (decision=" + decision.state() + ")");
        }
        Instant now = clock.now();
        survivor.mergeFrom(source, cmd.expectedVersion(), now);
        repo.update(survivor);
        // Source is tombstoned with the same merge signature.
        source.tombstone(source.version(), now);
        repo.update(source);
        repo.removeCanonicalKey(source.id(),
                CanonicalKey.of(source.treeId(), source.givenName() == null ? "" : source.givenName(),
                        source.surname() == null ? "" : source.surname(), source.birthDate()));
        publisher.publish(new MemberMerged(survivor.treeId(), survivor.id(), source.id(),
                survivor.version(), 1L, now));
        publisher.publish(new com.familya.member.domain.event.MemberTombstoned(
                source.treeId(), source.id(), source.version(), 1L, now));
        LOG.info("Merged source={} into survivor={} actingUser={}", source.id(), survivor.id(), cmd.actingUser());
    }

    public interface Clock { Instant now(); }
}