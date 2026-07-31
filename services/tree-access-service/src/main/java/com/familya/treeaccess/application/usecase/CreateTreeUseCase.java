package com.familya.treeaccess.application.usecase;

import com.familya.treeaccess.application.port.in.CreateTreeCommand;
import com.familya.treeaccess.application.port.out.TreeEventPublisher;
import com.familya.treeaccess.application.port.out.TreeRepository;
import com.familya.treeaccess.domain.event.TreeCreated;
import com.familya.treeaccess.domain.model.AuthorizationProjection;
import com.familya.treeaccess.domain.model.Tree;
import com.familya.treeaccess.domain.model.TreeMembership;
import com.familya.platform.outbox.OutboxWriter;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Creates a tree and an owner membership. The owner is granted
 * effective ADMIN by virtue of tree.ownerUserId matching userId (the
 * membership row carries ADMIN but the invariant is also enforced in
 * {@link TreeMembership.Role#effectiveRole()} via the projection).
 */
@Service
public class CreateTreeUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(CreateTreeUseCase.class);

    /** Kho lưu trữ cây. */
    private final TreeRepository repo;

    /** Bộ publish sự kiện. */
    private final TreeEventPublisher publisher;

    /** Metric giám sát. */
    private final PlatformMetrics metrics;

    /** Đồng hồ tiêm được. */
    private final Clock clock;

    /**
     * Khởi tạo use-case.
     *
     * @param repo      kho lưu trữ
     * @param publisher bộ publish sự kiện
     * @param metrics   metric giám sát
     * @param clock     đồng hồ
     */
    public CreateTreeUseCase(TreeRepository repo, TreeEventPublisher publisher,
                             PlatformMetrics metrics, Clock clock) {
        this.repo = repo;
        this.publisher = publisher;
        this.metrics = metrics;
        this.clock = clock;
    }

    /**
     * Tạo cây mới và cấp projection ADMIN cho chủ sở hữu. Thao tác này phát
     * đồng thời sự kiện {@link TreeCreated} để các service khác cập nhật.
     *
     * @param cmd lệnh tạo cây (chứa ownerUserId, name)
     * @return mã cây vừa tạo
     */
    @Transactional
    public UUID execute(CreateTreeCommand cmd) {
        metrics.mutationAcceptedCounter("tree-access-service", "createTree").increment();
        Instant now = clock.now();
        UUID treeId = UUID.randomUUID();
        Tree tree = new Tree(treeId, cmd.name(), cmd.ownerUserId(),
                Tree.State.ACTIVE, 1L, 1L, now, null, null, 0L);
        TreeMembership owner = new TreeMembership(
                UUID.randomUUID(), treeId, cmd.ownerUserId(), TreeMembership.Role.ADMIN,
                cmd.ownerUserId(), now, null, null, null);
        repo.insertTree(tree, owner);
        // Cấp projection ADMIN ngay cho owner để tra cứu phân quyền trả về ADMIN mà không cần đợi event.
        repo.upsertProjection(new AuthorizationProjection(
                treeId, cmd.ownerUserId(), TreeMembership.Role.ADMIN,
                tree.revision(), tree.epoch(), now, false, null, now));
        publisher.publishTreeEvent(new TreeCreated(treeId, cmd.ownerUserId(), cmd.name(),
                tree.revision(), tree.epoch(), now));
        LOG.info("Created tree id={} owner={} name={}", treeId, cmd.ownerUserId(), cmd.name());
        return treeId;
    }

    /** Interface đồng hồ cho use-case. */
    public interface Clock { Instant now(); }
}