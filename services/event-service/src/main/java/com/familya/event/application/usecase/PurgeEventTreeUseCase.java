package com.familya.event.application.usecase;

import com.familya.event.application.port.in.PurgeEventTreeCommand;
import com.familya.event.application.port.out.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Use case <b>purge (tombstone hàng loạt)</b> mọi sự kiện của một cây
 * — bước tham gia của event-service trong <b>Saga xóa cây</b>
 * (delete-tree Saga).
 *
 * <h2>Luồng xử lý</h2>
 * <ol>
 *   <li>Gọi {@link EventRepository#bulkTombstoneByTree} để đánh dấu
 *       tombstone tất cả sự kiện chưa xóa trong cây.</li>
 *   <li>Tính {@code appliedAggregateVersion} là tối đa giữa
 *       {@code targetAggregateVersion} và {@code 0} để tránh trả về giá
 *       trị âm do cắt ép dữ liệu.</li>
 *   <li>Ghi log audit và trả về kết quả.</li>
 * </ol>
 *
 * <p>Lưu ý: đây là tombstone mềm; dữ liệu vẫn còn để khôi phục qua
 * {@link RestoreEventTreeUseCase} nếu Saga cần rollback.
 *
 * @author gia-pha platform
 */
@Service
public class PurgeEventTreeUseCase {

    /** Logger dùng cho audit. */
    private static final Logger LOG = LoggerFactory.getLogger(PurgeEventTreeUseCase.class);

    private final EventRepository repo;

    /**
     * Khởi tạo use case.
     *
     * @param repo kho lưu trữ.
     */
    public PurgeEventTreeUseCase(EventRepository repo) {
        this.repo = repo;
    }

    /**
     * Thực thi purge cây.
     *
     * @param cmd lệnh purge từ Saga.
     * @return {@link Result} chứa số sự kiện đã ảnh hưởng, phiên bản
     *         và epoch đã áp dụng.
     */
    @Transactional
    public Result execute(PurgeEventTreeCommand cmd) {
        // Bước 1: lấy thời điểm hiện tại làm mốc tombstone.
        Instant now = Instant.now();

        // Bước 2: ủy quyền cho repository thực hiện tombstone hàng loạt.
        int affected = repo.bulkTombstoneByTree(cmd.treeId(), now);

        // Bước 3: ghi log audit.
        LOG.info("Bulk-tombstoned {} events on tree {} operationId={}",
                affected, cmd.treeId(), cmd.operationId());

        // Bước 4: trả về kết quả — appliedAggregateVersion tối thiểu 0
        // để tránh giá trị âm do cắt ép dữ liệu khi target < 0.
        return new Result(affected, Math.max(0L, cmd.targetAggregateVersion()), cmd.targetEpoch());
    }

    /**
     * Kết quả của việc purge cây.
     *
     * @param affectedCount           số sự kiện đã được tombstone.
     * @param appliedAggregateVersion phiên bản aggregate được áp dụng.
     * @param appliedEpoch            epoch được áp dụng.
     */
    public record Result(int affectedCount, long appliedAggregateVersion, long appliedEpoch) { }
}
