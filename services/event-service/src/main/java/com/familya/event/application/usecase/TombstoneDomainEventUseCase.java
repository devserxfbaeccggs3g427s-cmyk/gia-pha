package com.familya.event.application.usecase;

import com.familya.event.application.port.in.TombstoneDomainEventCommand;
import com.familya.event.application.port.out.EventAuthRepository;
import com.familya.event.application.port.out.EventChangePublisher;
import com.familya.event.application.port.out.EventRepository;
import com.familya.event.domain.event.EventTombstoned;
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
import java.util.function.Supplier;

/**
 * Use case <b>tombstone (xóa mềm)</b> một
 * {@link DomainEvent sự kiện gia phả}.
 *
 * <h2>Luồng nghiệp vụ chính</h2>
 * <ol>
 *   <li>Ghi nhận metric.</li>
 *   <li>Tải aggregate; nếu không thấy ném {@link EventNotFoundException}.</li>
 *   <li>Tra cứu quyền trên projection; thiếu/thu hồi → ném
 *       {@link ForbiddenException}.</li>
 *   <li>Chống stale projection.</li>
 *   <li>Gọi {@link DomainEvent#tombstone} (idempotent — nếu đã tombstone
 *       rồi thì không làm gì).</li>
 *   <li>Lưu trữ và phát {@link EventTombstoned} qua outbox.</li>
 * </ol>
 *
 * <p>Tombstone là thao tác <b>không phục hồi</b> trong use case này;
 * việc khôi phục được thực hiện bởi
 * {@link RestoreEventTreeUseCase} (nếu cần khôi phục cả cây) hoặc bởi
 * quy trình riêng (nếu cần khôi phục một sự kiện lẻ).
 *
 * @author gia-pha platform
 */
@Service
public class TombstoneDomainEventUseCase {

    /** Logger dùng cho audit. */
    private static final Logger LOG = LoggerFactory.getLogger(TombstoneDomainEventUseCase.class);

    private final EventRepository repo;
    private final EventChangePublisher publisher;
    private final EventAuthRepository authRepo;
    private final PlatformMetrics metrics;
    private final Supplier<Instant> clock;

    /**
     * Khởi tạo use case.
     *
     * @param repo      kho lưu trữ.
     * @param publisher bộ phát sự kiện.
     * @param authRepo  projection phân quyền.
     * @param metrics   metric.
     * @param clock     đồng hồ từ {@link CreateDomainEventUseCase.Clock}.
     */
    public TombstoneDomainEventUseCase(EventRepository repo, EventChangePublisher publisher,
                                        EventAuthRepository authRepo, PlatformMetrics metrics,
                                        CreateDomainEventUseCase.Clock clock) {
        this.repo = repo;
        this.publisher = publisher;
        this.authRepo = authRepo;
        this.metrics = metrics;
        // Bọc thành Supplier để giữ phong cách nhất quán nếu sau này thay đổi triển khai Clock.
        this.clock = clock::now;
    }

    /**
     * Thực thi tombstone sự kiện.
     *
     * @param cmd lệnh tombstone.
     * @throws EventNotFoundException    nếu sự kiện không tồn tại.
     * @throws ForbiddenException        nếu không có quyền.
     * @throws StaleProjectionException  nếu projection đã cũ.
     */
    @Transactional
    public void execute(TombstoneDomainEventCommand cmd) {
        // Bước 1: ghi nhận metric.
        metrics.mutationAccepted("event-service", "tombstoneEvent");

        // Bước 2: tải aggregate theo ID.
        DomainEvent ev = repo.findById(cmd.eventId())
                .orElseThrow(() -> new EventNotFoundException("Event " + cmd.eventId() + " not found"));

        // Bước 3: tra cứu quyền.
        var auth = authRepo.findAuth(ev.treeId(), cmd.actingUser())
                .orElseThrow(() -> new ForbiddenException(
                        "No auth projection tree=" + ev.treeId() + " user=" + cmd.actingUser()));

        // Bước 4: kiểm tra quyền.
        if (auth.isRevoked() || !auth.canEdit()) {
            throw new ForbiddenException("User " + cmd.actingUser() + " cannot edit tree " + ev.treeId());
        }

        // Bước 5: chống stale.
        if (auth.revision() < cmd.expectedTreeRevision()) {
            throw new StaleProjectionException(
                    "Auth projection revision " + auth.revision() + " < expected " + cmd.expectedTreeRevision());
        }

        // Bước 6: lấy thời điểm hiện tại từ clock.
        Instant now = clock.get();

        // Bước 7: ủy quyền cho aggregate (idempotent nếu đã tombstone).
        ev.tombstone(cmd.expectedVersion(), now);

        // Bước 8: lưu lại.
        repo.update(ev);

        // Bước 9: phát sự kiện để đồng bộ projection downstream.
        publisher.publish(new EventTombstoned(ev.id(), ev.treeId(), ev.version(), now));

        // Bước 10: ghi log audit.
        LOG.info("Tombstoned event id={} version={} actingUser={}", ev.id(), ev.version(), cmd.actingUser());
    }
}
