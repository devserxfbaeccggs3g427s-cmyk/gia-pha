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

    /**
     * Khởi tạo use case gộp thành viên.
     */
    public MergeMembersUseCase(MemberRepository repo, MemberEventPublisher publisher,
                                AuthorizationProjection authz, PlatformMetrics metrics, Clock clock) {
        this.repo = repo;
        this.publisher = publisher;
        this.authz = authz;
        this.metrics = metrics;
        this.clock = clock;
    }

    /**
     * Gộp thành viên nguồn vào thành viên survivor: survivor kế thừa avatar thiếu,
     * nguồn bị tombstone với cùng chữ ký merge. Phát sự kiện MemberMerged và MemberTombstoned.
     *
     * @param cmd lệnh gộp
     * @throws MemberNotFoundException nếu một trong hai thành viên không tồn tại
     * @throws ForbiddenException      nếu người dùng không có quyền
     */
    @Transactional
    public void execute(MergeMembersCommand cmd) {
        metrics.mutationAccepted("member-service", "mergeMembers");
        // Tra cứu cả hai thành viên, báo lỗi nếu thiếu
        Member survivor = repo.findById(cmd.survivorId())
                .orElseThrow(() -> new MemberNotFoundException("Survivor " + cmd.survivorId() + " not found"));
        Member source = repo.findById(cmd.sourceMemberId())
                .orElseThrow(() -> new MemberNotFoundException("Source " + cmd.sourceMemberId() + " not found"));
        // Kiểm tra quyền dựa trên cây của survivor
        AuthorizationProjection.Decision<MemberAuthRow> decision =
                authz.authorize(survivor.treeId(), cmd.actingUser(), cmd.expectedTreeRevision(), MemberAuthRow.class);
        if (!decision.isAllowed()) {
            throw new ForbiddenException(
                    "User " + cmd.actingUser() + " cannot edit tree " + survivor.treeId()
                            + " (decision=" + decision.state() + ")");
        }
        Instant now = clock.now();
        // Aggregate merge: survivor kế thừa avatar nếu thiếu
        survivor.mergeFrom(source, cmd.expectedVersion(), now);
        repo.update(survivor);
        // Nguồn bị tombstone với cùng chữ ký merge
        source.tombstone(source.version(), now);
        repo.update(source);
        // Gỡ canonical key của nguồn để tránh trùng lặp khi truy vấn
        repo.removeCanonicalKey(source.id(),
                CanonicalKey.of(source.treeId(), source.givenName() == null ? "" : source.givenName(),
                        source.surname() == null ? "" : source.surname(), source.birthDate()));
        // Phát hai sự kiện: MemberMerged cho survivor và MemberTombstoned cho nguồn
        publisher.publish(new MemberMerged(survivor.treeId(), survivor.id(), source.id(),
                survivor.version(), 1L, now));
        publisher.publish(new com.familya.member.domain.event.MemberTombstoned(
                source.treeId(), source.id(), source.version(), 1L, now));
        LOG.info("Merged source={} into survivor={} actingUser={}", source.id(), survivor.id(), cmd.actingUser());
    }

    /** Đồng hồ tiêm được cho use case. */
    public interface Clock { Instant now(); }
}