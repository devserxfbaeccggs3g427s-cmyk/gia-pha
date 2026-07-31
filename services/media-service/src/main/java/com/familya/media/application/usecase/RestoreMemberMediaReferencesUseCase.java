package com.familya.media.application.usecase;

import com.familya.media.application.port.in.RestoreMemberMediaReferencesCommand;
import com.familya.media.application.port.out.MediaReferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case bù trừ (compensation) cho {@code delete-member Saga}: khôi
 * phục các tham chiếu loại {@code MEMBER} đã bị xóa bởi
 * {@link DetachMemberMediaReferencesUseCase}.
 *
 * <p>{@code DetachMemberMediaReferencesUseCase} đã lưu snapshot bù trừ
 * theo {@code operationId}; use case này gọi
 * {@link MediaReferenceRepository#restoreMemberReferences(UUID)} để
 * phát lại chính xác các tham chiếu từ snapshot, đảm bảo rollback
 * an toàn.</p>
 *
 * <p>Luồng nghiệp vụ chính:</p>
 * <ol>
 *   <li>Yêu cầu {@code MediaReferenceRepository} phát lại tham chiếu từ
 *       snapshot theo {@code operationId}.</li>
 *   <li>Trả về số tham chiếu đã khôi phục.</li>
 * </ol>
 */
@Service
public class RestoreMemberMediaReferencesUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(RestoreMemberMediaReferencesUseCase.class);

    private final MediaReferenceRepository refs;

    public RestoreMemberMediaReferencesUseCase(MediaReferenceRepository refs) {
        this.refs = refs;
    }

    /**
     * Thực thi use case.
     *
     * @param cmd lệnh khôi phục; xem {@link RestoreMemberMediaReferencesCommand}.
     * @return {@link Result} mô tả số tham chiếu đã khôi phục.
     */
    @Transactional
    public Result execute(RestoreMemberMediaReferencesCommand cmd) {
        // Phát lại tham chiếu từ compensation snapshot lưu theo operationId.
        int restored = refs.restoreMemberReferences(cmd.operationId());
        LOG.info("Restored {} media references for member {} operationId={}",
                restored, cmd.memberId(), cmd.operationId());
        return new Result(restored, 0L, 0L);
    }

    /**
     * Kết quả trả về cho Saga coordinator.
     *
     * @param affectedCount          số tham chiếu đã khôi phục.
     * @param appliedAggregateVersion phiên bản aggregate (luôn {@code 0}
     *                               vì restore không đẩy version).
     * @param appliedEpoch           epoch (luôn {@code 0}).
     */
    public record Result(int affectedCount, long appliedAggregateVersion, long appliedEpoch) { }
}