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

    private final TreeRepository repo;
    private final TreeEventPublisher publisher;
    private final PlatformMetrics metrics;
    private final Clock clock;

    public AdvanceDeleteMemberRevisionUseCase(TreeRepository repo, TreeEventPublisher publisher,
                                              PlatformMetrics metrics, Clock clock) {
        this.repo = repo;
        this.publisher = publisher;
        this.metrics = metrics;
        this.clock = clock;
    }

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

    public record Result(long appliedAggregateVersion, long appliedEpoch) { }

    public interface Clock { Instant now(); }
}