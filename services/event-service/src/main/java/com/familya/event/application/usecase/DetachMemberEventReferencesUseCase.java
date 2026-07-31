package com.familya.event.application.usecase;

import com.familya.event.application.port.in.DetachMemberEventReferencesCommand;
import com.familya.event.application.port.out.EventRepository;
import com.familya.event.domain.model.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Use case <b>gỡ tham chiếu thành viên</b> khỏi mọi sự kiện liên quan —
 * bước tham gia của event-service trong <b>Saga xóa thành viên</b>
 * (delete-member Saga).
 *
 * <h2>Khác biệt với tombstone</h2>
 * <p>Không giống tombstone (xóa mềm toàn bộ sự kiện), use case này chỉ
 * <b>gỡ ID thành viên</b> khỏi:
 * <ul>
 *   <li>{@code primaryMemberId} — chuyển thành {@code null} nếu trùng.</li>
 *   <li>{@code additionalMemberIds} — loại khỏi danh sách.</li>
 * </ul>
 * Bản thân sự kiện vẫn còn "sống", chỉ là không còn tham chiếu tới
 * thành viên đã xóa. Điều này cho phép:
 * <ul>
 *   <li>Lịch sử sự kiện gia phả vẫn được bảo toàn.</li>
 *   <li>Saga có thể rollback bằng
 *       {@link RestoreMemberEventReferencesUseCase}.</li>
 * </ul>
 *
 * <h2>Luồng xử lý</h2>
 * <ol>
 *   <li>Tra cứu các sự kiện đang tham chiếu tới {@code memberId} (không
 *       bao gồm tombstone) bằng
 *       {@link EventRepository#listReferencingMember}.</li>
 *   <li>Với mỗi sự kiện:
 *     <ul>
 *       <li>Lọc bỏ {@code memberId} khỏi {@code additionalMemberIds}.</li>
 *       <li>Nếu {@code primaryMemberId == memberId} → chuyển thành {@code null}.</li>
 *       <li>Gọi {@link DomainEvent#update} với các giá trị mới; không thay
 *           đổi tiêu đề/ngày/địa điểm nhưng vẫn tăng version cho audit.</li>
 *     </ul>
 *   </li>
 *   <li>Tính {@code appliedAggregateVersion} là tối đa giữa version đã
 *       tăng và {@code targetAggregateVersion} từ Saga.</li>
 *   <li>Trả về kết quả cho listener để phát SagaReply.</li>
 * </ol>
 *
 * <p>Việc lưu compensation snapshot được đảm nhận bởi adapter hoặc bước
 * trước đó của Saga; use case này chỉ chịu trách nhiệm cập nhật payload.
 *
 * @author gia-pha platform
 */
@Service
public class DetachMemberEventReferencesUseCase {

    /** Logger dùng cho audit. */
    private static final Logger LOG = LoggerFactory.getLogger(DetachMemberEventReferencesUseCase.class);

    private final EventRepository repo;

    /**
     * Khởi tạo use case.
     *
     * @param repo kho lưu trữ.
     */
    public DetachMemberEventReferencesUseCase(EventRepository repo) {
        this.repo = repo;
    }

    /**
     * Thực thi gỡ tham chiếu thành viên.
     *
     * @param cmd lệnh từ Saga.
     * @return {@link Result} mô tả số sự kiện đã ảnh hưởng và phiên bản/epoch
     *         đã áp dụng.
     */
    @Transactional
    public Result execute(DetachMemberEventReferencesCommand cmd) {
        // Bước 1: liệt kê sự kiện (chưa tombstone) đang tham chiếu tới memberId.
        List<DomainEvent> referencing = repo.listReferencingMember(cmd.treeId(), cmd.memberId());

        // Bước 2: lấy thời điểm hiện tại cho touch().
        Instant now = Instant.now();
        long maxVersion = 0L;

        // Bước 3: duyệt từng sự kiện và gỡ tham chiếu.
        for (DomainEvent ev : referencing) {
            // Bước 3a: lọc danh sách thành viên phụ.
            List<UUID> additional = ev.additionalMemberIds().stream()
                    .filter(id -> !id.equals(cmd.memberId()))
                    .toList();

            // Bước 3b: thực hiện cập nhật; nếu primary trùng thì set null.
            ev.update(ev.title(), ev.description(), ev.kind(),
                    ev.startDate(), ev.endDate(), ev.recurrence(),
                    ev.primaryMemberId() != null && ev.primaryMemberId().equals(cmd.memberId())
                            ? null : ev.primaryMemberId(),
                    additional, ev.mediaRefs(), ev.location(),
                    ev.version(), now);

            // Bước 3c: lưu lại.
            repo.update(ev);

            // Bước 3d: cập nhật version tối đa.
            maxVersion = Math.max(maxVersion, ev.version());
        }

        // Bước 4: appliedAggregateVersion = max(version thực tế, target từ Saga).
        long appliedVersion = Math.max(maxVersion, cmd.targetAggregateVersion());
        long appliedEpoch = cmd.targetEpoch();

        // Bước 5: ghi log audit.
        LOG.info("Detached member {} from {} events on tree {} operationId={}",
                cmd.memberId(), referencing.size(), cmd.treeId(), cmd.operationId());

        // Bước 6: trả về kết quả.
        return new Result(referencing.size(), appliedVersion, appliedEpoch);
    }

    /**
     * Kết quả của việc gỡ tham chiếu.
     *
     * @param affectedCount           số sự kiện đã bị thay đổi.
     * @param appliedAggregateVersion phiên bản aggregate đã áp dụng.
     * @param appliedEpoch            epoch đã áp dụng.
     */
    public record Result(int affectedCount, long appliedAggregateVersion, long appliedEpoch) { }
}
