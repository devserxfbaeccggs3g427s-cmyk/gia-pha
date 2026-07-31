package com.familya.member.application.usecase;

import com.familya.member.application.port.in.CreateMemberCommand;
import com.familya.member.application.port.out.MemberEventPublisher;
import com.familya.member.application.port.out.MemberRepository;
import com.familya.member.domain.event.MemberCreated;
import com.familya.member.domain.exception.DuplicateMemberException;
import com.familya.member.domain.model.CanonicalKey;
import com.familya.member.domain.model.Member;
import com.familya.member.domain.model.MemberAuthRow;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.error.StaleProjectionException;
import com.familya.platform.outbox.OutboxWriter;
import com.familya.platform.projection.AuthorizationProjection;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Member profile CRUD. Authorisation uses the local membership
 * projection via the platform SDK; an absent or stale row blocks the
 * mutation (deny-on-stale). Tombstoned members stay hidden from
 * reads.
 */
@Service
public class CreateMemberUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(CreateMemberUseCase.class);

    private final MemberRepository repo;
    private final MemberEventPublisher publisher;
    private final AuthorizationProjection authz;
    private final OutboxWriter outbox;
    private final PlatformMetrics metrics;
    private final Clock clock;

    /**
     * Khởi tạo use case với các phụ thuộc cần thiết.
     *
     * @param repo      kho thành viên
     * @param publisher cổng phát sự kiện (outbox)
     * @param authz     projection ủy quyền
     * @param outbox    writer outbox (giữ để tiện sử dụng tương lai; hiện không dùng trực tiếp)
     * @param metrics   bộ metric
     * @param clock     đồng hồ tiêm được
     */
    public CreateMemberUseCase(MemberRepository repo, MemberEventPublisher publisher,
                                AuthorizationProjection authz, OutboxWriter outbox,
                                PlatformMetrics metrics, Clock clock) {
        this.repo = repo;
        this.publisher = publisher;
        this.authz = authz;
        this.outbox = outbox;
        this.metrics = metrics;
        this.clock = clock;
    }

    /**
     * Thực thi tạo thành viên: kiểm tra quyền, chống trùng lặp theo canonical key, chèn và
     * phát sự kiện MemberCreated.
     *
     * @param cmd lệnh tạo thành viên
     * @return mã thành viên vừa tạo
     * @throws ForbiddenException         nếu người dùng không có quyền chỉnh sửa cây
     * @throws DuplicateMemberException   nếu đã tồn tại thành viên cùng canonical key
     */
    @Transactional
    public UUID execute(CreateMemberCommand cmd) {
        metrics.mutationAccepted("member-service", "createMember");
        Instant now = clock.now();
        // Kiểm tra quyền: người dùng phải có khả năng chỉnh sửa cây này.
        AuthorizationProjection.Decision<MemberAuthRow> decision =
                authz.authorize(cmd.treeId(), cmd.actingUser(), cmd.expectedTreeRevision(), MemberAuthRow.class);
        if (!decision.isAllowed()) {
            throw new ForbiddenException(
                    "User " + cmd.actingUser() + " cannot edit tree " + cmd.treeId()
                            + " (decision=" + decision.state() + ")");
        }
        // Xây dựng canonical key từ tên + ngày sinh (thiếu thì dùng chuỗi rỗng)
        CanonicalKey key = CanonicalKey.of(cmd.treeId(), cmd.givenName() == null ? "" : cmd.givenName(),
                cmd.surname() == null ? "" : cmd.surname(), cmd.birthDate());
        // Chống trùng lặp: nếu đã có thành viên cùng key thì báo lỗi
        Optional<UUID> existing = repo.findByCanonicalKey(key);
        if (existing.isPresent()) {
            throw new DuplicateMemberException(
                    "Member with canonical key already exists: " + existing.get());
        }
        // Tạo aggregate Member mới với version=0 và tombstonedAt=null
        UUID id = UUID.randomUUID();
        Member m = new Member(
                id, cmd.treeId(), cmd.userId(),
                cmd.displayName(), cmd.givenName(), cmd.surname(),
                cmd.birthDate(), cmd.deathDate(),
                cmd.birthYearKnown(), cmd.deathYearKnown(),
                cmd.gender(), cmd.status() == null ? Member.Status.LIVING : cmd.status(),
                cmd.generation(), cmd.legacyAvatarUrl(), cmd.notes(),
                now, now, null, 0L);
        repo.insert(m);
        // Ghi canonical key để tra cứu trùng lặp trong tương lai
        repo.insertCanonicalKey(key, id);
        // Phát sự kiện MemberCreated qua outbox (transactional)
        publisher.publish(new MemberCreated(cmd.treeId(), id, cmd.userId(), cmd.displayName(),
                m.gender(), m.status(), 1L, 1L, now));
        LOG.info("Created member id={} tree={} actingUser={}", id, cmd.treeId(), cmd.actingUser());
        return id;
    }

    /** Đồng hồ tiêm được cho use case — cho phép kiểm thử xác định. */
    public interface Clock { Instant now(); }
}