package com.familya.member.application.usecase;

import com.familya.member.application.port.in.LoadMemberCommand;
import com.familya.member.application.port.out.MemberRepository;
import com.familya.member.domain.exception.DuplicateMemberException;
import com.familya.member.domain.model.CanonicalKey;
import com.familya.member.domain.model.Member;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent migration loader for the member service. Each manifest
 * line carries the original UUID and timestamp; rerunning with the
 * same {@code memberId} is a no-op when {@code replaySafe=true}.
 */
@Service
public class LoadMemberUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(LoadMemberUseCase.class);

    private final MemberRepository repo;
    private final PlatformMetrics metrics;

    public LoadMemberUseCase(MemberRepository repo, PlatformMetrics metrics) {
        this.repo = repo;
        this.metrics = metrics;
    }

    @Transactional
    public LoadResult execute(LoadMemberCommand cmd) {
        metrics.mutationAccepted("member-service", "loadMember");
        if (repo.findById(cmd.memberId()).isPresent()) {
            return new LoadResult(cmd.memberId(), LoadResult.Status.DUPLICATE);
        }
        Member m = new Member(
                cmd.memberId(), cmd.treeId(), cmd.userId(),
                cmd.displayName(), cmd.givenName(), cmd.surname(),
                cmd.birthDate(), cmd.deathDate(),
                cmd.birthYearKnown(), cmd.deathYearKnown(),
                cmd.gender() == null ? null : Member.Gender.valueOf(cmd.gender()),
                Member.Status.valueOf(cmd.status()),
                cmd.generation(), cmd.legacyAvatarUrl(), cmd.notes(),
                cmd.createdAt(), cmd.updatedAt(),
                cmd.tombstoned() ? cmd.updatedAt() : null, 0L);
        try {
            repo.insert(m);
        } catch (org.springframework.dao.DuplicateKeyException dup) {
            return new LoadResult(cmd.memberId(), LoadResult.Status.DUPLICATE);
        }
        if (cmd.givenName() != null && cmd.surname() != null) {
            CanonicalKey key = CanonicalKey.of(cmd.treeId(), cmd.givenName(), cmd.surname(), cmd.birthDate());
            try {
                repo.insertCanonicalKey(key, cmd.memberId());
            } catch (org.springframework.dao.DuplicateKeyException dup) {
                // Different member with same key — quarantine per ADR-009. We skip but
                // log loudly so the migration & reconciliation pipeline can flag it.
                LOG.warn("Canonical-key collision member={} key={}", cmd.memberId(), key);
                throw new DuplicateMemberException("Canonical-key collision for member " + cmd.memberId());
            }
        }
        LOG.info("Loaded member id={} tree={}", cmd.memberId(), cmd.treeId());
        return new LoadResult(cmd.memberId(), LoadResult.Status.LOADED);
    }

    public record LoadResult(java.util.UUID memberId, Status status) {
        public enum Status { LOADED, DUPLICATE }
    }
}