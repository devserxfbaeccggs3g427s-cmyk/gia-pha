package com.familya.media.application.usecase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.familya.media.application.port.in.DetachMemberMediaReferencesCommand;
import com.familya.media.application.port.out.MediaReferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Use case tham gia vào {@code delete-member Saga} ở phía media-service.
 *
 * <p>Luồng nghiệp vụ chính:</p>
 * <ol>
 *   <li>Liệt kê toàn bộ tham chiếu loại {@code MEMBER} trỏ tới member
 *       bị xóa trong cùng tree.</li>
 *   <li>Lưu snapshot bù trừ (compensation snapshot) theo {@code operationId}
 *       để có thể phát lại khi Saga fail trước ranh giới không-thể-đảo.</li>
 *   <li>Xóa lần lượt các tham chiếu khỏi
 *       {@code MediaReferenceRepository}.</li>
 *   <li>Trả về số tham chiếu đã tách, kèm phiên bản aggregate và epoch
 *       để Saga theo dõi.</li>
 * </ol>
 *
 * <p>Binary vật lý KHÔNG bị xóa trong use case này; cleanup worker sẽ
 * xóa sau khi retention hold hết hạn.</p>
 */
@Service
public class DetachMemberMediaReferencesUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(DetachMemberMediaReferencesUseCase.class);

    private final MediaReferenceRepository refs;
    private final ObjectMapper json;

    public DetachMemberMediaReferencesUseCase(MediaReferenceRepository refs, ObjectMapper json) {
        this.refs = refs;
        this.json = json;
    }

    /**
     * Thực thi use case.
     *
     * @param cmd lệnh tách tham chiếu; xem {@link DetachMemberMediaReferencesCommand}.
     * @return {@link Result} mô tả số tham chiếu đã tách và phiên bản
     *         áp dụng (cho Saga).
     */
    @Transactional
    public Result execute(DetachMemberMediaReferencesCommand cmd) {
        // Bước 1: liệt kê các tham chiếu MEMBER hiện có cho member bị xóa.
        List<MediaReferenceRepository.ReferenceRow> rows =
                refs.listByTarget(cmd.treeId(), "MEMBER", cmd.memberId());
        // Bước 2: lưu snapshot đã serialize để bước restore có thể
        // tái tạo lại đúng trạng thái.
        refs.saveCompensationSnapshot(cmd.operationId(), serializeSnapshot(rows));
        // Bước 3: xóa từng tham chiếu. Nếu có lỗi giữa chừng, transaction
        // sẽ rollback toàn bộ; bước restore không cần chạy vì không có
        // thay đổi.
        for (MediaReferenceRepository.ReferenceRow row : rows) {
            refs.clear(row.mediaId(), "MEMBER", cmd.memberId());
        }
        LOG.info("Detached {} media references for member {} on tree {} operationId={}",
                rows.size(), cmd.memberId(), cmd.treeId(), cmd.operationId());
        // appliedAggregateVersion được bảo vệ bằng Math.max(0L, ...) để
        // chống giá trị âm từ upstream; epoch được lấy nguyên từ Saga.
        return new Result(rows.size(),
                Math.max(0L, cmd.targetAggregateVersion()),
                cmd.targetEpoch());
    }

    /**
     * Serialize danh sách tham chiếu thành JSON cho compensation snapshot.
     *
     * <p>Cấu trúc: {@code {"rows": [{mediaId, treeId, status,
     * lastAttemptAt}, ...]}} dùng {@link LinkedHashMap} để giữ thứ tự
     * trường ổn định.</p>
     *
     * @param rows danh sách tham chiếu cần serialize.
     * @return chuỗi JSON.
     * @throws IllegalStateException nếu Jackson không thể serialize.
     */
    private String serializeSnapshot(List<MediaReferenceRepository.ReferenceRow> rows) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("rows", rows.stream().map(r -> Map.of(
                "mediaId", r.mediaId().toString(),
                "treeId", r.treeId().toString(),
                "status", r.status(),
                "lastAttemptAt", r.lastAttemptAt() == null ? "" : r.lastAttemptAt().toString()
        )).toList());
        try {
            return json.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize media compensation snapshot", e);
        }
    }

    /**
     * Kết quả trả về cho Saga coordinator.
     *
     * @param affectedCount          số tham chiếu đã tách.
     * @param appliedAggregateVersion phiên bản aggregate được áp dụng
     *                               (đã chặn giá trị âm).
     * @param appliedEpoch           epoch Saga.
     */
    public record Result(int affectedCount, long appliedAggregateVersion, long appliedEpoch) { }
}