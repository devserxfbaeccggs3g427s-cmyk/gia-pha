package com.familya.event.application.usecase;

import com.familya.event.application.port.in.RestoreEventTreeCommand;
import com.familya.event.application.port.out.EventRepository;
import com.familya.event.domain.model.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Use case <b>khôi phục (compensating)</b> cho Saga xóa cây — hủy
 * tombstone trên mọi sự kiện của cây đã bị purge trước đó.
 *
 * <h2>Luồng xử lý</h2>
 * <ol>
 *   <li>Liệt kê <b>tất cả</b> sự kiện của cây (kể cả đã tombstone) bằng
 *       {@link EventRepository#listByTree(UUID, boolean)}.</li>
 *   <li>Với mỗi sự kiện đang tombstone, tạo một aggregate mới với
 *       {@code tombstonedAt = null} và {@code version += 1} (giữ nguyên
 *       {@code revision}, các trường payload khác).</li>
 *   <li>Gọi {@link EventRepository#update} để lưu.</li>
 *   <li>Trả về kết quả: số sự kiện đã phục hồi, phiên bản tối đa đã
 *       được áp dụng.</li>
 * </ol>
 *
 * <p>Đây là bước bù (compensation) trong Saga xóa cây — được gọi khi
 * một bước khác trong Saga thất bại và cần rollback.
 *
 * @author gia-pha platform
 */
@Service
public class RestoreEventTreeUseCase {

    /** Logger dùng cho audit. */
    private static final Logger LOG = LoggerFactory.getLogger(RestoreEventTreeUseCase.class);

    private final EventRepository repo;

    /**
     * Khởi tạo use case.
     *
     * @param repo kho lưu trữ.
     */
    public RestoreEventTreeUseCase(EventRepository repo) {
        this.repo = repo;
    }

    /**
     * Thực thi khôi phục cây.
     *
     * @param cmd lệnh khôi phục từ Saga.
     * @return {@link Result} mô tả kết quả.
     */
    @Transactional
    public Result execute(RestoreEventTreeCommand cmd) {
        // Bước 1: liệt kê tất cả sự kiện của cây (kể cả tombstone) để có thể
        // phát hiện những cái cần phục hồi.
        List<DomainEvent> all = repo.listByTree(cmd.treeId(), true);

        // Bước 2: lấy thời điểm hiện tại dùng làm updatedAt mới.
        Instant now = Instant.now();
        long maxVersion = 0L;
        int restored = 0;

        // Bước 3: duyệt từng sự kiện, chỉ xử lý những cái đang tombstone.
        for (DomainEvent ev : all) {
            if (!ev.isTombstoned()) continue;

            // Bước 3a: tái tạo aggregate mới — giữ nguyên ID/revision/timestamps
            // gốc nhưng loại bỏ tombstonedAt và tăng version để phản ánh thay đổi.
            DomainEvent alive = new DomainEvent(
                    ev.id(), ev.treeId(), ev.title(), ev.description(), ev.kind(),
                    ev.startDate(), ev.endDate(), ev.recurrence(),
                    ev.primaryMemberId(), ev.additionalMemberIds(), ev.mediaRefs(),
                    ev.location(), ev.revision(), ev.createdAt(), now, null, ev.version() + 1);

            // Bước 3b: lưu trữ.
            repo.update(alive);

            // Bước 3c: cập nhật chỉ số.
            maxVersion = Math.max(maxVersion, alive.version());
            restored++;
        }

        // Bước 4: log audit và trả về kết quả.
        LOG.info("Restored {} events on tree {} operationId={}",
                restored, cmd.treeId(), cmd.operationId());
        return new Result(restored, maxVersion, 0L);
    }

    /**
     * Kết quả của việc khôi phục cây.
     *
     * @param affectedCount           số sự kiện đã được phục hồi.
     * @param appliedAggregateVersion phiên bản aggregate cao nhất đã chạm.
     * @param appliedEpoch            epoch đã áp dụng (mặc định 0 cho bước này).
     */
    public record Result(int affectedCount, long appliedAggregateVersion, long appliedEpoch) { }
}
