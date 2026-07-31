package com.familya.member.application.usecase;

import com.familya.member.application.port.in.LoadMemberCommand;
import com.familya.member.application.port.out.MemberRepository;
import com.familya.member.domain.exception.DuplicateMemberException;
import com.familya.member.domain.model.CanonicalKey;
import com.familya.member.domain.model.Member;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent migration loader for the member service. Each manifest
 * line carries the original UUID and timestamp; rerunning with the
 * same {@code memberId} is a no-op when {@code replaySafe=true}.
 */
@Service
public class LoadMemberUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(LoadMemberUseCase.class);

    private final MemberRepository repo;
    private final PlatformMetrics metrics;

    /**
     * Khởi tạo use case nạp thành viên.
     *
     * @param repo    kho thành viên
     * @param metrics bộ metric
     */
    public LoadMemberUseCase(MemberRepository repo, PlatformMetrics metrics) {
        this.repo = repo;
        this.metrics = metrics;
    }

    /**
     * Nạp một thành viên từ manifest di trú. Idempotent: gọi lại với cùng {@code memberId}
     * trả về {@code DUPLICATE} thay vì tạo bản ghi mới.
     *
     * @param cmd lệnh nạp
     * @return kết quả chứa mã thành viên và trạng thái {@code LOADED}/{@code DUPLICATE}
     */
    @Transactional
    public LoadResult execute(LoadMemberCommand cmd) {
        metrics.mutationAccepted("member-service", "loadMember");
        // Kiểm tra đã tồn tại hay chưa: idempotency ở cấp memberId
        if (repo.findById(cmd.memberId()).isPresent()) {
            return new LoadResult(cmd.memberId(), LoadResult.Status.DUPLICATE);
        }
        // Tạo aggregate Member với các timestamp gốc được bảo toàn
        Member m = new Member(
                cmd.memberId(), cmd.treeId(), cmd.userId(),
                cmd.displayName(), cmd.givenName(), cmd.surname(),
                cmd.birthDate(), cmd.deathDate(),
                cmd.birthYearKnown(), cmd.birthYearKnown(),
                cmd.gender() == null ? null : Member.Gender.valueOf(cmd.gender()),
                Member.Status.valueOf(cmd.status()),
                cmd.generation(), cmd.legacyAvatarUrl(), cmd.notes(),
                cmd.createdAt(), cmd.updatedAt(),
                cmd.tombstoned() ? cmd.updatedAt() : null, 0L);
        try {
            repo.insert(m);
        } catch (org.springframework.dao.DuplicateKeyException dup) {
            // Bản ghi đã tồn tại do race: trả về DUPLICATE thay vì lỗi
            return new LoadResult(cmd.memberId(), LoadResult.Status.DUPLICATE);
        }
        // Chèn canonical key nếu có đủ thông tin tên
        if (cmd.givenName() != null && cmd.surname() != null) {
            CanonicalKey key = CanonicalKey.of(cmd.treeId(), cmd.givenName(), cmd.surname(), cmd.birthDate());
            try {
                repo.insertCanonicalKey(key, cmd.memberId());
            } catch (org.springframework.dao.DuplicateKeyException dup) {
                // Thành viên khác cùng canonical key — cách ly theo ADR-009. Bỏ qua nhưng
                // ghi log to để pipeline di trú & đối chiếu có thể gắn cờ.
                LOG.warn("Canonical-key collision member={} key={}", cmd.memberId(), key);
                throw new DuplicateMemberException("Canonical-key collision for member " + cmd.memberId());
            }
        }
        LOG.info("Loaded member id={} tree={}", cmd.memberId(), cmd.treeId());
        return new LoadResult(cmd.memberId(), LoadResult.Status.LOADED);
    }

    /**
     * Kết quả của use case nạp thành viên.
     *
     * @param memberId mã thành viên đã xử lý
     * @param status   trạng thái: LOADED nếu tạo mới, DUPLICATE nếu đã tồn tại
     */
    public record LoadResult(java.util.UUID memberId, Status status) {
        /** Trạng thái kết quả nạp. */
        public enum Status { LOADED, DUPLICATE }
    }
}