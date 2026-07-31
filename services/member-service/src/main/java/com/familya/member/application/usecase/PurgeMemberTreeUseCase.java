package com.familya.member.application.usecase;

import com.familya.member.application.port.in.PurgeMemberTreeCommand;
import com.familya.member.application.port.out.MemberRepository;
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

    /**
     * Khởi tạo use case với kho thành viên.
     *
     * @param repo kho thành viên
     */
    public PurgeMemberTreeUseCase(MemberRepository repo) {
        this.repo = repo;
    }

    /**
     * Thực hiện bulk tombstone mọi thành viên chưa tombstone trong cây.
     *
     * @param cmd lệnh purge từ Saga delete-tree
     * @return kết quả gồm số thành viên đã ảnh hưởng, phiên bản và epoch đã áp dụng
     */
    @Transactional
    public Result execute(PurgeMemberTreeCommand cmd) {
        Instant now = Instant.now();
        // Sử dụng bulk tombstone của repository để giữ transaction ngắn ngay cả trên cây lớn
        int affected = repo.bulkTombstoneByTree(cmd.treeId(), now);
        LOG.info("Bulk-tombstoned {} members on tree {} operationId={}",
                affected, cmd.treeId(), cmd.operationId());
        return new Result(affected, Math.max(0L, cmd.targetAggregateVersion()), cmd.targetEpoch());
    }

    /**
     * Kết quả của use case purge.
     *
     * @param affectedCount          số thành viên đã bị tombstone
     * @param appliedAggregateVersion phiên bản aggregate mục tiêu
     * @param appliedEpoch           epoch mục tiêu
     */
    public record Result(int affectedCount, long appliedAggregateVersion, long appliedEpoch) { }
}