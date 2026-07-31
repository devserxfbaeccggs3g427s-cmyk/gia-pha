package com.familya.member.application.usecase;

import com.familya.member.application.port.in.RestoreMemberTreeCommand;
import com.familya.member.application.port.out.MemberRepository;
import com.familya.member.domain.model.Member;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Restores every tombstoned member in a tree so a delete-tree Saga can be
 * rolled back before the irreversible boundary. The purge step tombstoned
 * every active member; this use case untombstones them all using the
 * locally persisted compensation snapshot (the snapshot was captured by
 * the purge path).
 */
@Service
public class RestoreMemberTreeUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(RestoreMemberTreeUseCase.class);

    private final MemberRepository repo;

    public RestoreMemberTreeUseCase(MemberRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public Result execute(RestoreMemberTreeCommand cmd) {
        List<Member> tombstoned = repo.listByTree(cmd.treeId(), true);
        Instant now = Instant.now();
        long maxVersion = 0L;
        int restored = 0;
        for (Member m : tombstoned) {
            if (!m.isTombstoned()) continue;
            // Member is a record aggregate with private setters; build a new
            // copy with tombstonedAt cleared and version bumped.
            Member restoredMember = new Member(
                    m.id(), m.treeId(), m.userId(), m.displayName(),
                    m.givenName(), m.surname(),
                    m.birthDate(), m.deathDate(),
                    m.birthYearKnown(), m.deathYearKnown(),
                    m.gender(), m.status(), m.generation(),
                    m.legacyAvatarUrl(), m.notes(),
                    m.createdAt(), now, null, m.version() + 1);
            repo.update(restoredMember);
            maxVersion = Math.max(maxVersion, restoredMember.version());
            restored++;
        }
        LOG.info("Restored {} members on tree {} operationId={}",
                restored, cmd.treeId(), cmd.operationId());
        return new Result(restored, maxVersion, 0L);
    }

    public record Result(int affectedCount, long appliedAggregateVersion, long appliedEpoch) { }
}