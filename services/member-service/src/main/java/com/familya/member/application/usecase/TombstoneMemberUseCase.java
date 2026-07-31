package com.familya.member.application.usecase;

import com.familya.member.application.port.in.TombstoneMemberCommand;
import com.familya.member.application.port.out.MemberEventPublisher;
import com.familya.member.application.port.out.MemberRepository;
import com.familya.member.domain.event.MemberTombstoned;
import com.familya.member.domain.exception.MemberNotFoundException;
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
public class TombstoneMemberUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(TombstoneMemberUseCase.class);

    private final MemberRepository repo;
    private final MemberEventPublisher publisher;
    private final AuthorizationProjection authz;
    private final PlatformMetrics metrics;
    private final Clock clock;

    /**
     * Khởi tạo use case tombstone.
     */
    public TombstoneMemberUseCase(MemberRepository repo, MemberEventPublisher publisher,
                                   AuthorizationProjection authz, PlatformMetrics metrics, Clock clock) {
        this.repo = repo;
        this.publisher = publisher;
        this.authz = authz;
        this.metrics = metrics;
        this.clock = clock;
    }

    /**
     * Tombstone trực tiếp một thành viên (dùng cho trường hợp nội bộ, không qua Saga).
     *
     * @param cmd lệnh tombstone
     * @throws MemberNotFoundException nếu thành viên không tồn tại
     * @throws ForbiddenException      nếu người dùng không có quyền
     */
    @Transactional
    public void execute(TombstoneMemberCommand cmd) {
        metrics.mutationAccepted("member-service", "tombstoneMember");
        Member m = repo.findById(cmd.memberId())
                .orElseThrow(() -> new MemberNotFoundException("Member " + cmd.memberId() + " not found"));
        // Kiểm tra quyền trên cây của thành viên
        AuthorizationProjection.Decision<MemberAuthRow> decision =
                authz.authorize(m.treeId(), cmd.actingUser(), cmd.expectedTreeRevision(), MemberAuthRow.class);
        if (!decision.isAllowed()) {
            throw new ForbiddenException(
                    "User " + cmd.actingUser() + " cannot edit tree " + m.treeId()
                            + " (decision=" + decision.state() + ")");
        }
        Instant now = clock.now();
        // Tombstone có thể idempotent: nếu đã tombstone rồi thì member.tombstone() không làm gì
        m.tombstone(cmd.expectedVersion(), now);
        repo.update(m);
        // Phát sự kiện MemberTombstoned
        publisher.publish(new MemberTombstoned(m.treeId(), m.id(), m.version(), 1L, now));
        LOG.info("Tombstoned member id={} version={} actingUser={}", m.id(), m.version(), cmd.actingUser());
    }

    /** Đồng hồ tiêm được cho use case. */
    public interface Clock { Instant now(); }
}