package com.familya.event.application.usecase;

import com.familya.event.application.port.in.RestoreMemberEventReferencesCommand;
import com.familya.event.application.port.out.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case <b>khôi phục tham chiếu thành viên</b> đã bị gỡ bởi
 * {@link DetachMemberEventReferencesUseCase} — đây là bước bù
 * (compensation) khi Saga xóa thành viên cần rollback.
 *
 * <h2>Luồng xử lý</h2>
 * <ol>
 *   <li>Tra cứu snapshot bù đã lưu bằng {@code operationId}.
 *       Nếu không có → không có việc gì để rollback (Saga đã chạy tới
 *       bước khác hoặc compensation trước đó đã thực thi).</li>
 *   <li>Ủy quyền cho
 *       {@link EventRepository#restoreMemberReferences} để áp dụng
 *       snapshot trở lại các sự kiện.</li>
 *   <li>Ghi log và trả về kết quả.</li>
 * </ol>
 *
 * <p>Việc lưu snapshot bù được thực hiện bởi bước detach (xem
 * {@link DetachMemberEventReferencesUseCase}); use case này chỉ chịu
 * trách nhiệm áp dụng snapshot.
 *
 * @author gia-pha platform
 */
@Service
public class RestoreMemberEventReferencesUseCase {

    /** Logger dùng cho audit. */
    private static final Logger LOG = LoggerFactory.getLogger(RestoreMemberEventReferencesUseCase.class);

    private final EventRepository repo;

    /**
     * Khởi tạo use case.
     *
     * @param repo kho lưu trữ.
     */
    public RestoreMemberEventReferencesUseCase(EventRepository repo) {
        this.repo = repo;
    }

    /**
     * Thực thi khôi phục tham chiếu.
     *
     * @param cmd lệnh compensation từ Saga.
     * @return {@link Result} mô tả số sự kiện đã được khôi phục.
     */
    @Transactional
    public Result execute(RestoreMemberEventReferencesCommand cmd) {
        // Bước 1: tra cứu snapshot bù.
        String snapshot = repo.loadCompensationSnapshot(cmd.operationId());
        if (snapshot == null) {
            // Không có snapshot — coi như compensation đã hoàn tất trước đó.
            LOG.warn("No compensation snapshot for operationId={} (likely already restored)",
                    cmd.operationId());
            return new Result(0, 0L, 0L);
        }

        // Bước 2: ủy quyền cho repository áp dụng snapshot.
        int restored = repo.restoreMemberReferences(cmd.operationId(), cmd.memberId());

        // Bước 3: log audit.
        LOG.info("Restored member references on {} events for operationId={}",
                restored, cmd.operationId());

        // Bước 4: appliedAggregateVersion = 1 nếu có restore, 0 nếu không — đây
        // là heuristic cho Saga biết compensation có hiệu lực.
        return new Result(restored, restored == 0 ? 0L : 1L, 0L);
    }

    /**
     * Kết quả của việc khôi phục tham chiếu.
     *
     * @param affectedCount           số sự kiện đã được khôi phục.
     * @param appliedAggregateVersion phiên bản aggregate đã áp dụng (heuristic).
     * @param appliedEpoch            epoch đã áp dụng.
     */
    public record Result(int affectedCount, long appliedAggregateVersion, long appliedEpoch) { }
}
