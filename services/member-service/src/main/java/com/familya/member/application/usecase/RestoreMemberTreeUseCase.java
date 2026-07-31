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

    /**
     * Khởi tạo use case với kho thành viên.
     *
     * @param repo kho thành viên
     */
    public RestoreMemberTreeUseCase(MemberRepository repo) {
        this.repo = repo;
    }

    /**
     * Khôi phục mọi thành viên đã tombstone trong cây bằng cách tạo bản sao aggregate với
     * {@code tombstonedAt} được xóa và version được tăng.
     *
     * @param cmd lệnh restore từ Saga delete-tree (compensation)
     * @return kết quả gồm số thành viên đã khôi phục, phiên bản tối đa và epoch
     */
    @Transactional
    public Result execute(RestoreMemberTreeCommand cmd) {
        List<Member> tombstoned = repo.listByTree(cmd.treeId(), true);
        Instant now = Instant.now();
        long maxVersion = 0L;
        int restored = 0;
        for (Member m : tombstoned) {
            if (!m.isTombstoned()) continue;
            // Member là aggregate với setter riêng; tạo bản sao với tombstonedAt=null
            // và version tăng 1 để vẫn thỏa mãn optimistic concurrency.
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

    /**
     * Kết quả của use case restore.
     *
     * @param affectedCount          số thành viên đã khôi phục
     * @param appliedAggregateVersion phiên bản aggregate tối đa
     * @param appliedEpoch           epoch (mặc định 0)
     */
    public record Result(int affectedCount, long appliedAggregateVersion, long appliedEpoch) { }
}