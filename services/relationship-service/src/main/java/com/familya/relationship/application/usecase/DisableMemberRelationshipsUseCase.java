package com.familya.relationship.application.usecase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.familya.relationship.application.port.in.DisableMemberRelationshipsCommand;
import com.familya.relationship.application.port.out.RelationshipRepository;
import com.familya.relationship.domain.model.Relationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bước tham gia của Relationship service trong saga xóa thành viên (do Member
 * service điều phối).
 *
 * <h2>Nhiệm vụ bắt buộc</h2>
 * <ol>
 *   <li>Tombstone mọi cạnh đang hoạt động có liên quan tới {@code memberId}
 *       (cả ở chiều {@code fromMemberId} và {@code toMemberId}).</li>
 *   <li>Lưu <b>snapshot bồi thường</b> để {@code RestoreMemberRelationshipsUseCase}
 *       có thể undo nếu cần.</li>
 *   <li>Tính {@code appliedAggregateVersion} và {@code appliedEpoch} từ các
 *       giá trị trong command và trả về cho orchestrator kiểm tra barrier.</li>
 * </ol>
 */
@Service
public class DisableMemberRelationshipsUseCase {

    /** Logger ghi nhận hoạt động. */
    private static final Logger LOG = LoggerFactory.getLogger(DisableMemberRelationshipsUseCase.class);

    /** Repository thao tác với DB. */
    private final RelationshipRepository repo;
    /** Trình phân tích JSON. */
    private final ObjectMapper json;

    /**
     * Khởi tạo use case.
     *
     * @param repo repository
     * @param json trình phân tích JSON
     */
    public DisableMemberRelationshipsUseCase(RelationshipRepository repo, ObjectMapper json) {
        this.repo = repo;
        this.json = json;
    }

    /**
     * Thực thi vô hiệu hóa quan hệ của một thành viên.
     *
     * @param cmd lệnh disable
     * @return kết quả gồm số cạnh bị ảnh hưởng và phiên bản áp dụng
     */
    @Transactional
    public Result execute(DisableMemberRelationshipsCommand cmd) {
        // Bước 1: nạp tất cả cạnh đang hoạt động có liên quan tới member.
        List<Relationship> active = repo.listActiveByMember(cmd.treeId(), cmd.memberId());
        // Bước 2: lưu snapshot bồi thường TRƯỚC khi thay đổi, để có thể undo.
        repo.saveCompensationSnapshot(cmd.operationId(), serialize(active));

        // Bước 3: tombstone từng cạnh; theo dõi version cao nhất để trả về.
        Instant now = Instant.now();
        long maxVersion = 0L;
        for (Relationship r : active) {
            // tombstone() trên aggregate đảm bảo version tăng đúng (version + 1).
            r.tombstone(r.version(), now);
            repo.update(r);
            maxVersion = Math.max(maxVersion, r.version());
        }

        // Bước 4: appliedVersion và appliedEpoch lấy max để orchestrator nhận
        // giá trị cao nhất giữa kỳ vọng và mục tiêu.
        long appliedVersion = Math.max(maxVersion, cmd.targetAggregateVersion());
        long appliedEpoch = Math.max(cmd.expectedEpoch(), cmd.targetEpoch());

        LOG.info("Disabled {} edges for member {} on tree {} operationId={}",
                active.size(), cmd.memberId(), cmd.treeId(), cmd.operationId());
        return new Result(active.size(), appliedVersion, appliedEpoch);
    }

    /**
     * Tuần tự hóa danh sách các cạnh thành JSON để lưu snapshot bồi thường.
     * <p>
     * Cấu trúc JSON: {@code {"memberId": "<treeId của cạnh đầu>", "edges": [...]}}.
     * Trường {@code memberId} trong JSON thực chất đang lưu {@code treeId} của
     * quan hệ đầu tiên (giữ tương thích với mã downstream).
     * </p>
     *
     * @param active danh sách quan hệ đang hoạt động
     * @return chuỗi JSON của snapshot
     * @throws IllegalStateException nếu việc tuần tự hóa thất bại
     */
    private String serialize(List<Relationship> active) {
        try {
            // Dùng LinkedHashMap để giữ thứ tự trường ổn định trong JSON (dễ test).
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("memberId", active.isEmpty() ? null : active.get(0).treeId().toString());
            // Danh sách các cạnh: id, from, to, kind, version.
            root.put("edges", active.stream().map(r -> Map.of(
                    "id", r.id().toString(),
                    "from", r.fromMemberId().toString(),
                    "to", r.toMemberId().toString(),
                    "kind", r.kind().name(),
                    "version", r.version())).toList());
            return json.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            // Lỗi tuần tự hóa hiếm gặp (chỉ xảy ra khi dữ liệu không hợp lệ);
            // gói lại để caller xử lý (thường sẽ fail saga).
            throw new IllegalStateException("Failed to serialize compensation snapshot", e);
        }
    }

    /**
     * Kết quả tham gia saga.
     *
     * @param affectedCount             số cạnh đã bị tombstone
     * @param appliedAggregateVersion   phiên bản aggregate đã áp dụng
     * @param appliedEpoch              epoch đã áp dụng
     */
    public record Result(int affectedCount, long appliedAggregateVersion, long appliedEpoch) { }
}