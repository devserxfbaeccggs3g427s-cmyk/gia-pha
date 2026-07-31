package com.familya.relationship.application.usecase;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.familya.relationship.application.port.in.RestoreMemberRelationshipsCommand;
import com.familya.relationship.application.port.out.RelationshipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Use case khôi phục các quan hệ đã bị vô hiệu hóa bởi
 * {@code DisableMemberRelationshipsUseCase} trong saga xóa thành viên.
 * <p>
 * Use case disable trước đó đã lưu một <b>snapshot bồi thường</b> (compensation
 * snapshot) dưới dạng JSON trong bảng {@code saga_compensation_snapshot}, khóa
 * theo {@code operationId}. Use case này đọc snapshot và lần lượt gọi
 * {@code Repository.untombstone} cho từng cạnh để khôi phục trạng thái.
 * </p>
 *
 * <h2>Định dạng snapshot</h2>
 * <pre>
 * {
 *   "memberId": "&lt;UUID&gt;",          // tham khảo, không dùng trực tiếp
 *   "edges": [
 *     { "id": "&lt;UUID&gt;", "from": "...", "to": "...", "kind": "...", "version": &lt;long&gt; },
 *     ...
 *   ]
 * }
 * </pre>
 */
@Service
public class RestoreMemberRelationshipsUseCase {

    /** Logger ghi nhận hoạt động. */
    private static final Logger LOG = LoggerFactory.getLogger(RestoreMemberRelationshipsUseCase.class);

    /** Repository thao tác với DB. */
    private final RelationshipRepository repo;
    /** Trình phân tích JSON. */
    private final ObjectMapper json;

    /**
     * Khởi tạo use case.
     *
     * @param repo repository
     * @param json trình phân tích JSON (được tiêm bởi Spring)
     */
    public RestoreMemberRelationshipsUseCase(RelationshipRepository repo, ObjectMapper json) {
        this.repo = repo;
        this.json = json;
    }

    /**
     * Thực thi khôi phục quan hệ cho thành viên.
     * <p>
     * Thuật toán:
     * </p>
     * <ol>
     *   <li>Tải snapshot bồi thường theo {@code operationId}.</li>
     *   <li>Nếu không có snapshot (đã được khôi phục trước đó hoặc không tồn
     *       tại) thì trả về kết quả rỗng và log cảnh báo.</li>
     *   <li>Phân tích JSON, duyệt từng cạnh và gọi {@code untombstone} với
     *       {@code version} đã chốt. Theo dõi {@code maxVersion} và đếm số cạnh.</li>
     *   <li>Trả về kết quả tổng hợp cho orchestrator.</li>
     * </ol>
     *
     * @param cmd lệnh khôi phục
     * @return kết quả gồm số cạnh đã khôi phục và phiên bản aggregate áp dụng
     */
    @Transactional
    public Result execute(RestoreMemberRelationshipsCommand cmd) {
        // Bước 1: tải snapshot bồi thường.
        String snapshot = repo.loadCompensationSnapshot(cmd.operationId());
        if (snapshot == null) {
            // Snapshot có thể không tồn tại vì:
            //   - Saga đã hoàn tất (compensation thành công) và snapshot đã được dọn.
            //   - operationId sai.
            // Trả về kết quả rỗng là phản hồi an toàn idempotent.
            LOG.warn("No compensation snapshot for operationId={} (likely already restored)", cmd.operationId());
            return new Result(0, 0L, 0L);
        }
        try {
            // Bước 2: parse JSON để duyệt từng cạnh.
            JsonNode root = json.readTree(snapshot);
            long maxVersion = 0L;
            int restored = 0;
            // Bước 3: duyệt mảng "edges", mỗi phần tử có id, version, ...
            for (JsonNode edge : root.path("edges")) {
                String edgeId = edge.path("id").asText();
                long version = edge.path("version").asLong();
                // Gọi untombstone với version chốt từ snapshot.
                repo.untombstone(java.util.UUID.fromString(edgeId), Instant.now(), version);
                // Cập nhật maxVersion cho barrier của orchestrator.
                maxVersion = Math.max(maxVersion, version + 1);
                restored++;
            }
            LOG.info("Restored {} relationship edges for operationId={}",
                    restored, cmd.operationId());
            return new Result(restored, maxVersion, 0L);
        } catch (Exception e) {
            // Bất kỳ lỗi phân tích JSON nào đều được gói trong IllegalStateException
            // để caller có thể nhận biết và xử lý phù hợp (thường là trả về FAILED).
            throw new IllegalStateException(
                    "Failed to deserialize compensation snapshot for operationId="
                            + cmd.operationId(), e);
        }
    }

    /**
     * Kết quả khôi phục.
     *
     * @param affectedCount             số cạnh đã được untombstone
     * @param appliedAggregateVersion   phiên bản aggregate cao nhất áp dụng
     * @param appliedEpoch              epoch (hiện tại = 0)
     */
    public record Result(int affectedCount, long appliedAggregateVersion, long appliedEpoch) { }
}