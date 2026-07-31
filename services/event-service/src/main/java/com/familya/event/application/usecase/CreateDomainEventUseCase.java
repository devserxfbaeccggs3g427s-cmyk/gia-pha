package com.familya.event.application.usecase;

import com.familya.event.application.port.in.CreateDomainEventCommand;
import com.familya.event.application.port.out.EventAuthRepository;
import com.familya.event.application.port.out.EventChangePublisher;
import com.familya.event.application.port.out.EventRepository;
import com.familya.event.application.port.out.ReferenceAvailability;
import com.familya.event.domain.event.EventCreated;
import com.familya.event.domain.exception.DanglingReferenceException;
import com.familya.event.domain.model.DomainEvent;
import com.familya.event.domain.model.RecurrenceRule;
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
 * Use case <b>tạo mới</b> một {@link DomainEvent sự kiện gia phả}.
 *
 * <h2>Luồng nghiệp vụ chính</h2>
 * <ol>
 *   <li>Ghi nhận metric <i>mutation accepted</i> để quan sát hoạt động.</li>
 *   <li>Tra cứu quyền của {@code actingUser} trên {@code treeId} từ
 *       {@link EventAuthRepository projection phân quyền cục bộ}.
 *       Quyền phải chưa bị thu hồi và phải có vai trò
 *       {@code ADMIN}/{@code EDITOR}.</li>
 *   <li>Kiểm tra <i>revision</i> của projection phải đủ mới (chống stale).</li>
 *   <li>Xác thực các tham chiếu (member + media) phải tồn tại trong
 *       projection tương ứng — nếu thiếu sẽ ném
 *       {@link DanglingReferenceException}.</li>
 *   <li>Áp dụng quy tắc ngày nhuận cho neo ngày bắt đầu kèm recurrence
 *       (chuyển 29/2 về 28/2 ở năm không nhuận).</li>
 *   <li>Tạo aggregate với {@code revision=1}, {@code version=0}.</li>
 *   <li>Lưu vào cơ sở dữ liệu và phát {@link EventCreated} qua outbox.</li>
 *   <li>Ghi log audit và trả về {@code id} của sự kiện.</li>
 * </ol>
 *
 * <h2>Ngoại lệ có thể xảy ra</h2>
 * <ul>
 *   <li>{@link ForbiddenException} — thiếu quyền hoặc bị thu hồi.</li>
 *   <li>{@link StaleProjectionException} — projection chưa đủ mới.</li>
 *   <li>{@link DanglingReferenceException} — tham chiếu không tồn tại.</li>
 *   <li>{@link IllegalArgumentException} — tham số không hợp lệ (qua aggregate).</li>
 * </ul>
 *
 * @author gia-pha platform
 */
@Service
public class CreateDomainEventUseCase {

    /** Logger dùng cho audit/observability. */
    private static final Logger LOG = LoggerFactory.getLogger(CreateDomainEventUseCase.class);

    private final EventRepository repo;
    private final EventChangePublisher publisher;
    private final EventAuthRepository authRepo;
    private final ReferenceAvailability refs;
    private final PlatformMetrics metrics;
    private final Clock clock;

    /**
     * Khởi tạo use case với các phụ thuộc bắt buộc.
     *
     * @param repo      kho lưu trữ aggregate.
     * @param publisher bộ phát hành sự kiện (qua outbox).
     * @param authRepo  projection phân quyền.
     * @param refs      kiểm tra tham chiếu khả dụng.
     * @param metrics   chỉ số quan sát.
     * @param clock     đồng hồ lấy thời điểm hiện tại (testable).
     */
    public CreateDomainEventUseCase(EventRepository repo, EventChangePublisher publisher,
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
     * Thực thi tạo mới sự kiện theo {@link CreateDomainEventCommand}.
     *
     * <p>Phương thức được đánh dấu {@link Transactional} — đảm bảo ghi
     * DB và stage outbox là <i>atomic</i>.
     *
     * @param cmd lệnh tạo sự kiện từ REST/Saga.
     * @return định danh sự kiện vừa tạo.
     * @throws ForbiddenException        nếu người dùng không có quyền.
     * @throws StaleProjectionException  nếu projection quá cũ.
     * @throws DanglingReferenceException nếu có tham chiếu không hợp lệ.
     */
    @Transactional
    public UUID execute(CreateDomainEventCommand cmd) {
        // Bước 1: ghi nhận metric đầu vào (đếm số mutation đã chấp nhận).
        metrics.mutationAccepted("event-service", "createEvent");

        // Bước 2: tra cứu quyền; nếu không có bản ghi phân quyền thì từ chối.
        var auth = authRepo.findAuth(cmd.treeId(), cmd.actingUser())
                .orElseThrow(() -> new ForbiddenException(
                        "No auth projection tree=" + cmd.treeId() + " user=" + cmd.actingUser()));

        // Bước 3: kiểm tra quyền có hiệu lực và có thể chỉnh sửa hay không.
        if (auth.isRevoked() || !auth.canEdit()) {
            throw new ForbiddenException("User " + cmd.actingUser() + " cannot edit tree " + cmd.treeId());
        }

        // Bước 4: chống stale projection — đảm bảo revision của projection phân quyền
        // phải bằng hoặc lớn hơn revision mà client đang giữ.
        if (auth.revision() < cmd.expectedTreeRevision()) {
            throw new StaleProjectionException(
                    "Auth projection revision " + auth.revision() + " < expected " + cmd.expectedTreeRevision());
        }

        // Bước 5: xác thực tất cả tham chiếu (member + media) tồn tại trong projection.
        validateReferences(cmd.treeId(), cmd.primaryMemberId(), cmd.additionalMemberIds(), cmd.mediaRefs(), refs);

        // Bước 6: lấy thời điểm hiện tại (qua Clock để dễ test).
        Instant now = clock.now();

        // Bước 7: áp dụng quy tắc ngày nhuận cho anchor kèm recurrence.
        java.time.LocalDate start = cmd.startDate();
        if (start != null && cmd.recurrence() != null) {
            // Legacy: 29/2 ở năm không nhuận neo về 28/2.
            start = RecurrenceRule.applyLeapDay(start, start);
        }

        // Bước 8: sinh UUID và tạo aggregate mới (revision=1, version=0).
        UUID id = UUID.randomUUID();
        DomainEvent ev = new DomainEvent(
                id, cmd.treeId(), cmd.title(), cmd.description(), cmd.kind(),
                start, cmd.endDate(), cmd.recurrence(),
                cmd.primaryMemberId(),
                cmd.additionalMemberIds() == null ? java.util.List.of() : cmd.additionalMemberIds(),
                cmd.mediaRefs() == null ? java.util.List.of() : cmd.mediaRefs(),
                cmd.location(),
                1L, now, now, null, 0L);

        // Bước 9: lưu trữ vĩnh viễn.
        repo.insert(ev);

        // Bước 10: phát hành sự kiện EventCreated qua outbox.
        publisher.publish(new EventCreated(id, cmd.treeId(), cmd.kind(), cmd.title(), ev.version(), now));

        // Bước 11: ghi log audit.
        LOG.info("Created event id={} tree={} actingUser={}", id, cmd.treeId(), cmd.actingUser());

        return id;
    }

    /**
     * Phương thức tĩnh tiện ích: xác thực một tập tham chiếu có thuộc
     * về projection khả dụng hay không.
     *
     * <p>Quy trình xử lý:
     * <ol>
     *   <li>Duyệt {@code primary} và {@code additional} → kiểm tra
     *       {@link ReferenceAvailability#isMemberAvailable(UUID, UUID)}.</li>
     *   <li>Duyệt {@code media} → kiểm tra
     *       {@link ReferenceAvailability#isMediaAvailable(UUID, UUID)}.</li>
     *   <li>Tập hợp các ID "đứt" vào một {@link HashSet} để tránh trùng lặp.</li>
     *   <li>Nếu tập khác rỗng, ném {@link DanglingReferenceException}
     *       kèm thông điệp liệt kê tất cả ID vi phạm.</li>
     * </ol>
     *
     * @param treeId     cây gia phả.
     * @param primary    ID thành viên chính (có thể {@code null}).
     * @param additional danh sách ID thành viên phụ (có thể {@code null}).
     * @param media      danh sách ID media (có thể {@code null}).
     * @param refs       cổng kiểm tra tham chiếu.
     * @throws DanglingReferenceException nếu có bất kỳ tham chiếu nào đứt.
     */
    private static void validateReferences(UUID treeId, UUID primary, java.util.List<UUID> additional,
                                            java.util.List<UUID> media, ReferenceAvailability refs) {
        // Tập hợp tất cả ID "đứt" để báo lỗi tổng hợp thay vì từng cái.
        Set<UUID> dangling = new HashSet<>();

        // Ưu tiên kiểm tra primary vì đây là tham chiếu thường xuyên xuất hiện.
        if (primary != null && !refs.isMemberAvailable(treeId, primary)) dangling.add(primary);

        // Lọc null để tránh false-positive cho client gửi danh sách chứa null.
        if (additional != null) {
            for (UUID m : additional) {
                if (m != null && !refs.isMemberAvailable(treeId, m)) dangling.add(m);
            }
        }
        if (media != null) {
            for (UUID m : media) {
                if (m != null && !refs.isMediaAvailable(treeId, m)) dangling.add(m);
            }
        }

        // Ném lỗi tổng hợp để client có thể hiển thị toàn bộ vấn đề một lần.
        if (!dangling.isEmpty()) {
            throw new DanglingReferenceException(
                    "Dangling references for tree=" + treeId + ": " + dangling);
        }
    }

    /**
     * SPI đồng hồ — cho phép test inject thời gian cố định.
     */
    public interface Clock { Instant now(); }
}
