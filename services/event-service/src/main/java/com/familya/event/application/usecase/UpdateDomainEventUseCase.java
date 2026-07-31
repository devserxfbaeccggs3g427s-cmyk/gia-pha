package com.familya.event.application.usecase;

import com.familya.event.application.port.in.UpdateDomainEventCommand;
import com.familya.event.application.port.out.EventAuthRepository;
import com.familya.event.application.port.out.EventChangePublisher;
import com.familya.event.application.port.out.EventRepository;
import com.familya.event.application.port.out.ReferenceAvailability;
import com.familya.event.domain.event.EventCreated;
import com.familya.event.domain.exception.DanglingReferenceException;
import com.familya.event.domain.exception.EventNotFoundException;
import com.familya.event.domain.model.DomainEvent;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.error.StaleProjectionException;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Use case <b>cập nhật nội dung</b> của một
 * {@link DomainEvent sự kiện gia phả} đã tồn tại.
 *
 * <h2>Luồng nghiệp vụ chính</h2>
 * <ol>
 *   <li>Ghi nhận metric.</li>
 *   <li>Tải aggregate; nếu không thấy sẽ ném {@link EventNotFoundException}.</li>
 *   <li>Tra cứu quyền trên projection; nếu thiếu hoặc bị thu hồi sẽ
 *       ném {@link ForbiddenException}.</li>
 *   <li>Kiểm tra revision projection chống stale.</li>
 *   <li>Xác thực các tham chiếu mới; nếu có tham chiếu đứt sẽ ném
 *       {@link DanglingReferenceException}.</li>
 *   <li>Gọi {@link DomainEvent#update} (tự thực hiện tương tranh lạc quan).</li>
 *   <li>Lưu trữ và phát {@link EventCreated} (dùng lại cho update nhằm
 *       đơn giản hóa projection phía tiêu thụ).</li>
 * </ol>
 *
 * <p>Việc publish {@code EventCreated} cho cả update (thay vì một sự
 * kiện {@code EventUpdated} riêng) là quyết định có chủ đích: các
 * projection downstream chỉ cần xử lý một sự kiện dạng "nội dung mới".
 *
 * @author gia-pha platform
 */
@Service
public class UpdateDomainEventUseCase {

    /** Logger dùng cho audit. */
    private static final Logger LOG = LoggerFactory.getLogger(UpdateDomainEventUseCase.class);

    private final EventRepository repo;
    private final EventChangePublisher publisher;
    private final EventAuthRepository authRepo;
    private final ReferenceAvailability refs;
    private final PlatformMetrics metrics;
    private final Clock clock;

    /**
     * Khởi tạo use case với các phụ thuộc.
     *
     * @param repo      kho lưu trữ aggregate.
     * @param publisher bộ phát sự kiện (outbox).
     * @param authRepo  projection phân quyền.
     * @param refs      kiểm tra tham chiếu.
     * @param metrics   metric.
     * @param clock     đồng hồ testable.
     */
    public UpdateDomainEventUseCase(EventRepository repo, EventChangePublisher publisher,
                                     EventAuthRepository authRepo, ReferenceAvailability refs,
                                     PlatformMetrics metrics, Clock clock) {
        this.repo = repo;
        this.publisher = publisher;
        this.authRepo = authRepo;
        this.refs = refs;
        this.metrics = metrics;
        this.clock = clock;
    }

    /**
     * Thực thi cập nhật sự kiện theo {@link UpdateDomainEventCommand}.
     *
     * @param cmd lệnh cập nhật.
     * @throws EventNotFoundException    nếu sự kiện không tồn tại.
     * @throws ForbiddenException        nếu không có quyền.
     * @throws StaleProjectionException  nếu projection đã cũ.
     * @throws DanglingReferenceException nếu có tham chiếu đứt.
     */
    @Transactional
    public void execute(UpdateDomainEventCommand cmd) {
        // Bước 1: ghi nhận metric.
        metrics.mutationAccepted("event-service", "updateEvent");

        // Bước 2: tải aggregate theo eventId.
        DomainEvent ev = repo.findById(cmd.eventId())
                .orElseThrow(() -> new EventNotFoundException("Event " + cmd.eventId() + " not found"));

        // Bước 3: tra cứu quyền trên cây của sự kiện.
        var auth = authRepo.findAuth(ev.treeId(), cmd.actingUser())
                .orElseThrow(() -> new ForbiddenException(
                        "No auth projection tree=" + ev.treeId() + " user=" + cmd.actingUser()));

        // Bước 4: kiểm tra quyền có hiệu lực và có thể chỉnh sửa.
        if (auth.isRevoked() || !auth.canEdit()) {
            throw new ForbiddenException("User " + cmd.actingUser() + " cannot edit tree " + ev.treeId());
        }

        // Bước 5: chống stale projection.
        if (auth.revision() < cmd.expectedTreeRevision()) {
            throw new StaleProjectionException(
                    "Auth projection revision " + auth.revision() + " < expected " + cmd.expectedTreeRevision());
        }

        // Bước 6: xác thực tham chiếu; tập hợp tất cả vi phạm trước khi ném.
        Set<UUID> dangling = new HashSet<>();
        if (cmd.primaryMemberId() != null && !refs.isMemberAvailable(ev.treeId(), cmd.primaryMemberId())) {
            dangling.add(cmd.primaryMemberId());
        }
        if (cmd.additionalMemberIds() != null) {
            for (UUID m : cmd.additionalMemberIds()) {
                if (m != null && !refs.isMemberAvailable(ev.treeId(), m)) dangling.add(m);
            }
        }
        if (cmd.mediaRefs() != null) {
            for (UUID m : cmd.mediaRefs()) {
                if (m != null && !refs.isMediaAvailable(ev.treeId(), m)) dangling.add(m);
            }
        }
        if (!dangling.isEmpty()) {
            throw new DanglingReferenceException(
                    "Dangling references for tree=" + ev.treeId() + ": " + dangling);
        }

        // Bước 7: lấy thời điểm hiện tại.
        Instant now = clock.now();

        // Bước 8: giao cho aggregate tự xử lý cập nhật (bao gồm optimistic
        // concurrency, biến đổi thành bất biến qua touch()).
        ev.update(cmd.title(), cmd.description(), cmd.kind(),
                cmd.startDate(), cmd.endDate(), cmd.recurrence(),
                cmd.primaryMemberId(),
                cmd.additionalMemberIds() == null ? java.util.List.of() : cmd.additionalMemberIds(),
                cmd.mediaRefs() == null ? java.util.List.of() : cmd.mediaRefs(),
                cmd.location(),
                cmd.expectedVersion(), now);

        // Bước 9: lưu lại.
        repo.update(ev);

        // Bước 10: phát sự kiện (tận dụng EventCreated cho mọi thay đổi nội dung).
        publisher.publish(new EventCreated(ev.id(), ev.treeId(), ev.kind(), ev.title(), ev.version(), now));

        // Bước 11: ghi log audit.
        LOG.info("Updated event id={} version={} actingUser={}", ev.id(), ev.version(), cmd.actingUser());
    }

    /**
     * SPI đồng hồ — được tiêm vào để test với thời gian cố định.
     */
    public interface Clock { Instant now(); }
}
