package com.familya.treeaccess.application.usecase;

import com.familya.treeaccess.application.port.in.AdvanceDeleteMemberRevisionCommand;
import com.familya.treeaccess.application.port.out.TreeEventPublisher;
import com.familya.treeaccess.application.port.out.TreeRepository;
import com.familya.treeaccess.domain.event.TreeAdvancedRevision;
import com.familya.treeaccess.domain.exception.TreeNotFoundException;
import com.familya.treeaccess.domain.model.Tree;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Participant step for delete-member Saga. Advances the authoritative tree
 * revision/epoch after every participant has acked, and publishes a
 * {@link TreeAdvancedRevision} event so projections converge.
 */
@Service
public class AdvanceDeleteMemberRevisionUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(AdvanceDeleteMemberRevisionUseCase.class);

    /** Kho lưu trữ cây. */
    private final TreeRepository repo;

    /** Bộ publish sự kiện cây. */
    private final TreeEventPublisher publisher;

    /** Metric giám sát. */
    private final PlatformMetrics metrics;

    /** Đồng hồ tiêm được. */
    private final Clock clock;

    /**
     * Khởi tạo use-case.
     *
     * @param repo      kho lưu trữ cây
     * @param publisher bộ publish sự kiện
     * @param metrics   metric giám sát
     * @param clock     đồng hồ
     */
    public AdvanceDeleteMemberRevisionUseCase(TreeRepository repo, TreeEventPublisher publisher,
                                              PlatformMetrics metrics, Clock clock) {
        this.repo = repo;
        this.publisher = publisher;
        this.metrics = metrics;
        this.clock = clock;
    }

    /**
     * Áp dụng yêu cầu tăng revision của Saga delete-member:
     *
     * <ol>
     *   <li>Tải cây, ném {@link TreeNotFoundException} nếu không tồn tại.</li>
     *   <li>Gọi {@link Tree#advanceRevisionForSaga} với phiên bản kỳ vọng và revision/epoch mới.</li>
     *   <li>Cập nhật cây và phát sự kiện {@link TreeAdvancedRevision}.</li>
     *   <li>Tăng metric và log.</li>
     * </ol>
     *
     * @param cmd lệnh tăng revision
     * @return {@link Result} gồm revision/epoch đã áp dụng
     */
    @Transactional
    public Result execute(AdvanceDeleteMemberRevisionCommand cmd) {
        Tree tree = repo.findTree(cmd.treeId())
                .orElseThrow(() -> new TreeNotFoundException("Tree " + cmd.treeId() + " not found"));
        tree.advanceRevisionForSaga(cmd.expectedTreeVersion(), cmd.newRevision(), cmd.newEpoch());
        repo.updateTree(tree);

        Instant now = clock.now();
        publisher.publishTreeEvent(new TreeAdvancedRevision(
                tree.id(), tree.revision(), tree.epoch(),
                cmd.operationId(), "delete-member-saga", now));
        metrics.mutationAcceptedCounter("tree-access-service", "advanceRevision.delete-member").increment();

        LOG.info("Advanced delete-member Saga tree={} rev={} epoch={} operationId={}",
                tree.id(), tree.revision(), tree.epoch(), cmd.operationId());
        return new Result(tree.revision(), tree.epoch());
    }

    /**
     * Kết quả của use-case.
     *
     * @param appliedAggregateVersion revision đã áp dụng
     * @param appliedEpoch            epoch đã áp dụng
     */
    public record Result(long appliedAggregateVersion, long appliedEpoch) { }

    /** Interface đồng hồ cho use-case (giúp mock trong test). */
    public interface Clock { Instant now(); }
}