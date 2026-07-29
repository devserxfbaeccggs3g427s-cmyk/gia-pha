package com.familya.member.application.usecase;

import com.familya.member.application.port.in.PurgeMemberTreeCommand;
import com.familya.member.application.port.out.MemberRepository;
import com.familya.member.application.port.out.MemberRepository.BulkTombstoneResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Participant step for delete-tree Saga. Tombstones every non-tombstoned
 * member in the tree with a single bulk UPDATE statement so the transaction
 * stays short even on large trees.
 */
@Service
public class PurgeMemberTreeUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(PurgeMemberTreeUseCase.class);

    private final MemberRepository repo;

    public PurgeMemberTreeUseCase(MemberRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public Result execute(PurgeMemberTreeCommand cmd) {
        Instant now = Instant.now();
        BulkTombstoneResult result = repo.bulkTombstoneByTree(cmd.treeId(), now);
        LOG.info("Bulk-tombstoned {} members on tree {} operationId={}",
                result.affectedCount(), cmd.treeId(), cmd.operationId());
        return new Result(result.affectedCount(), result.maxAppliedVersion(), cmd.targetEpoch());
    }

    public record Result(int affectedCount, long appliedAggregateVersion, long appliedEpoch) { }
}