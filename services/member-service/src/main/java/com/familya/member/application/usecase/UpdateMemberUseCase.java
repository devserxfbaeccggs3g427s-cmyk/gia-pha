package com.familya.member.application.usecase;

import com.familya.member.application.port.in.UpdateMemberCommand;
import com.familya.member.application.port.out.MemberEventPublisher;
import com.familya.member.application.port.out.MemberRepository;
import com.familya.member.domain.event.MemberCreated;
import com.familya.member.domain.exception.MemberNotFoundException;
import com.familya.member.domain.exception.MemberTombstonedException;
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
public class UpdateMemberUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(UpdateMemberUseCase.class);

    private final MemberRepository repo;
    private final MemberEventPublisher publisher;
    private final AuthorizationProjection authz;
    private final PlatformMetrics metrics;
    private final Clock clock;

    public UpdateMemberUseCase(MemberRepository repo, MemberEventPublisher publisher,
                                AuthorizationProjection authz, PlatformMetrics metrics, Clock clock) {
        this.repo = repo;
        this.publisher = publisher;
        this.authz = authz;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public void execute(UpdateMemberCommand cmd) {
        metrics.mutationAccepted("member-service", "updateMember");
        Member m = repo.findById(cmd.memberId())
                .orElseThrow(() -> new MemberNotFoundException("Member " + cmd.memberId() + " not found"));
        if (m.isTombstoned()) {
            throw new MemberTombstonedException("Member " + cmd.memberId() + " is tombstoned");
        }
        AuthorizationProjection.Decision<MemberAuthRow> decision =
                authz.authorize(m.treeId(), cmd.actingUser(), cmd.expectedTreeRevision(), MemberAuthRow.class);
        if (!decision.isAllowed()) {
            throw new ForbiddenException(
                    "User " + cmd.actingUser() + " cannot edit tree " + m.treeId()
                            + " (decision=" + decision.state() + ")");
        }
        Instant now = clock.now();
        if (cmd.displayName() != null) m.rename(cmd.displayName(), cmd.expectedVersion(), now);
        m.updateProfile(cmd.givenName(), cmd.surname(), cmd.birthDate(), cmd.deathDate(),
                cmd.gender(), cmd.generation(), cmd.notes(), cmd.expectedVersion(), now);
        repo.update(m);
        publisher.publish(new MemberCreated(m.treeId(), m.id(), m.userId(), m.displayName(),
                m.gender(), m.status(), m.version(), 1L, now));
        LOG.info("Updated member id={} version={} actingUser={}", m.id(), m.version(), cmd.actingUser());
    }

    public interface Clock { Instant now(); }
}