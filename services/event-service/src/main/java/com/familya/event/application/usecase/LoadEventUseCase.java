package com.familya.event.application.usecase;

import com.familya.event.application.port.in.LoadEventCommand;
import com.familya.event.application.port.out.EventRepository;
import com.familya.event.domain.model.DomainEvent;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Use case <b>di trú dữ liệu (migration loader)</b> — nạp
 * {@link DomainEvent sự kiện gia phả} từ các manifest blob bất biến vào
 * cơ sở dữ liệu của event-service.
 *
 * <h2>Tính idempotent</h2>
 * <p>Quy trình nạp phải idempotent để có thể chạy lại khi gặp lỗi:
 * <ol>
 *   <li>Kiểm tra sự kiện đã tồn tại bằng {@link EventRepository#findById}.
 *       Nếu có → trả về {@link LoadResult.Status#DUPLICATE}.</li>
 *   <li>Nếu repository ném {@link DuplicateKeyException} (do ràng buộc
 *       duy nhất ở DB), cũng trả về {@code DUPLICATE} thay vì lỗi.</li>
 *   <li>Trong trường hợp thành công, trả về {@link LoadResult.Status#LOADED}.</li>
 * </ol>
 *
 * <h2>Bảo toàn dữ liệu gốc</h2>
 * <p>Việc nạp phải giữ nguyên {@code createdAt}, {@code updatedAt} và
 * trạng thái tombstone từ manifest để audit trail được liên tục xuyên
 * suốt quá trình di trú.
 *
 * @author gia-pha platform
 */
@Service
public class LoadEventUseCase {

    /** Logger dùng cho audit. */
    private static final Logger LOG = LoggerFactory.getLogger(LoadEventUseCase.class);

    private final EventRepository repo;
    private final PlatformMetrics metrics;

    /**
     * Khởi tạo use case.
     *
     * @param repo    kho lưu trữ.
     * @param metrics metric quan sát.
     */
    public LoadEventUseCase(EventRepository repo, PlatformMetrics metrics) {
        this.repo = repo;
        this.metrics = metrics;
    }

    /**
     * Thực thi nạp một sự kiện từ manifest.
     *
     * @param cmd lệnh nạp chứa đầy đủ thông tin và ID gốc.
     * @return {@link LoadResult} mô tả kết quả.
     */
    @Transactional
    public LoadResult execute(LoadEventCommand cmd) {
        // Bước 1: metric hành động.
        metrics.mutationAccepted("event-service", "loadEvent");

        // Bước 2: kiểm tra đã tồn tại — idempotent tầng application.
        if (repo.findById(cmd.eventId()).isPresent()) {
            return new LoadResult(cmd.eventId(), LoadResult.Status.DUPLICATE);
        }

        // Bước 3: tạo aggregate với thời gian và revision từ manifest gốc.
        // Lưu ý: tombstonedAt được tính từ updatedAt đối với bản ghi đã tombstone
        // để bảo toàn thông tin audit (khoảng thời gian xóa được mô phỏng qua updatedAt).
        DomainEvent ev = new DomainEvent(
                cmd.eventId(), cmd.treeId(), cmd.title(), cmd.description(), cmd.kind(),
                cmd.startDate(), cmd.endDate(), cmd.recurrence(),
                cmd.primaryMemberId(),
                cmd.additionalMemberIds() == null ? java.util.List.of() : cmd.additionalMemberIds(),
                cmd.mediaRefs() == null ? java.util.List.of() : cmd.mediaRefs(),
                cmd.location(),
                1L, cmd.createdAt(), cmd.updatedAt(),
                cmd.tombstoned() ? cmd.updatedAt() : null, 0L);

        // Bước 4: thử insert — có thể ném DuplicateKey do race condition
        // giữa hai luồng nạp song song; nắm bắt để đảm bảo idempotent.
        try {
            repo.insert(ev);
        } catch (DuplicateKeyException dup) {
            return new LoadResult(cmd.eventId(), LoadResult.Status.DUPLICATE);
        }

        // Bước 5: log audit và trả về kết quả thành công.
        LOG.info("Loaded event id={} tree={}", cmd.eventId(), cmd.treeId());
        return new LoadResult(cmd.eventId(), LoadResult.Status.LOADED);
    }

    /**
     * Kết quả của việc nạp một sự kiện từ manifest.
     *
     * @param eventId định danh sự kiện đã xử lý.
     * @param status trạng thái {@link Status}: LOADED hoặc DUPLICATE.
     */
    public record LoadResult(UUID eventId, Status status) {
        /**
         * Trạng thái của việc nạp.
         */
        public enum Status {
            /** Sự kiện đã được tạo mới. */
            LOADED,
            /** Sự kiện đã tồn tại — bỏ qua (idempotent). */
            DUPLICATE
        }
    }
}
