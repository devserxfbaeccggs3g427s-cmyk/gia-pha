package com.familya.search.application.usecase;

import com.familya.search.application.port.in.ReportQuery;
import com.familya.search.application.port.out.ReportRepository;
import com.familya.search.application.port.out.SearchAuthorization;
import com.familya.search.application.port.out.SearchWatermarkRepository;
import com.familya.search.domain.exception.StaleBarrierException;
import com.familya.search.domain.model.ReportSnapshot;
import com.familya.search.domain.model.RevisionBarrier;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.telemetry.PlatformMetrics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Use case tính (hoặc tái sử dụng) một báo cáo cho cây gia phả.
 *
 * <p>Service này không sinh ra báo cáo thực sự - nó chỉ đóng gói một payload
 * JSON mang thông tin đặc trưng cho {@link ReportSnapshot.Kind} và ghi nhớ
 * bản chụp kèm watermark để có thể tái sử dụng ở các lần truy vấn sau.</p>
 *
 * <p>Quy tắc chính:</p>
 * <ul>
 *   <li>Nếu client yêu cầu {@code requestedWatermark} lớn hơn barrier hiện
 *       tại, ném {@link StaleBarrierException}.</li>
 *   <li>Nếu đã có bản chụp cho cùng {@code (treeId, kind, watermark)} thì
 *       trả về bản chụp đó - tính "xác định" theo watermark.</li>
 *   <li>Nếu chưa có thì sinh payload mới, lưu lại rồi trả về.</li>
 * </ul>
 */
@Service
public class ComputeReportUseCase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SearchAuthorization authz;
    private final ReportRepository repo;
    private final SearchWatermarkRepository watermark;
    private final PlatformMetrics metrics;

    /**
     * Khởi tạo use case với các phụ thuộc bắt buộc.
     *
     * @param authz     cổng kiểm tra quyền.
     * @param repo      cổng lưu/đọc bản chụp báo cáo.
     * @param watermark cổng đọc barrier phiên bản.
     * @param metrics   cổng ghi nhận telemetry.
     */
    public ComputeReportUseCase(SearchAuthorization authz, ReportRepository repo,
                                  SearchWatermarkRepository watermark, PlatformMetrics metrics) {
        this.authz = authz;
        this.repo = repo;
        this.watermark = watermark;
        this.metrics = metrics;
    }

    /**
     * Thực thi tính báo cáo.
     *
     * @param q truy vấn chứa cây, người dùng, phiên bản, loại báo cáo và
     *          watermark tối thiểu (tuỳ chọn).
     * @return kết quả gồm {@link ReportSnapshot} và barrier để trả về client.
     * @throws ForbiddenException      nếu client không có quyền.
     * @throws StaleBarrierException   nếu barrier chưa đạt watermark yêu cầu.
     */
    @Transactional
    public Result execute(ReportQuery q) {
        // Bước 1: Kiểm tra quyền truy cập.
        SearchAuthorization.Decision d = authz.authorize(q.treeId(), q.actingUser(), q.expectedTreeRevision());
        if (!d.isAllowed()) {
            throw new ForbiddenException("Cannot compute report: " + d.reason());
        }
        // Bước 2: Đọc barrier, tính watermark nhỏ nhất - đó là watermark
        // mà mọi miền đều đã hội tụ.
        RevisionBarrier barrier = watermark.barrierFor(q.treeId());
        long watermarkValue = min(barrier);
        // Bước 3: Nếu client yêu cầu watermark cao hơn khả năng đáp ứng
        // thì từ chối để client biết dữ liệu chưa đủ mới.
        if (q.requestedWatermark() != null && watermarkValue < q.requestedWatermark()) {
            throw new StaleBarrierException(
                    "Report watermark " + watermarkValue + " < requested " + q.requestedWatermark());
        }
        ReportSnapshot.Kind kind = ReportSnapshot.Kind.valueOf(q.kind().toUpperCase());
        // Bước 4: Nếu đã có bản chụp cho cùng watermark - xác định theo
        // (treeId, kind, watermark) - thì trả về bản cũ, đảm bảo idempotent.
        if (repo.exists(q.treeId(), kind, watermarkValue)) {
            // deterministic for the same watermark
            var existing = repo.findById(UUID.nameUUIDFromBytes(
                    (q.treeId() + ":" + kind + ":" + watermarkValue).getBytes()));
            return new Result(existing.orElseGet(() -> synthesize(q.treeId(), kind, watermarkValue)), barrier);
        }
        // Bước 5: Chưa có - sinh payload mới, lưu lại rồi trả về.
        ReportSnapshot snap = synthesize(q.treeId(), kind, watermarkValue);
        repo.save(snap);
        metrics.mutationAccepted("search-service", "computeReport");
        return new Result(snap, barrier);
    }

    /**
     * Sinh bản chụp báo cáo mới với payload JSON tối thiểu.
     *
     * @param treeId    định danh cây.
     * @param kind      loại báo cáo.
     * @param watermark watermark mà bản chụp phản ánh.
     * @return bản chụp báo cáo mới.
     */
    private ReportSnapshot synthesize(UUID treeId, ReportSnapshot.Kind kind, long watermark) {
        // Payload được giữ ở mức tối thiểu - chỉ ghi nhận cây, loại,
        // watermark và thời điểm sinh. Khi service này phát triển,
        // payload sẽ được thay bằng nội dung thống kê thực sự.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("treeId", treeId.toString());
        payload.put("kind", kind.name());
        payload.put("watermark", watermark);
        payload.put("generatedAt", Instant.now().toString());
        String json;
        try {
            json = MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            // Lỗi tuần tự hoá là lỗi lập trình/khách quan, đóng gói lại.
            throw new IllegalStateException("Cannot serialize report payload", e);
        }
        return new ReportSnapshot(UUID.randomUUID(), treeId, kind, json, watermark, Instant.now());
    }

    /**
     * Tính watermark nhỏ nhất trong barrier - tức mức mà mọi miền đều đã đạt.
     *
     * @param barrier rào chắn phiên bản.
     * @return giá trị nhỏ nhất; {@code 0L} nếu barrier rỗng.
     */
    private long min(RevisionBarrier barrier) {
        long min = Long.MAX_VALUE;
        for (Long v : barrier.values().values()) {
            if (v != null && v < min) min = v;
        }
        return min == Long.MAX_VALUE ? 0L : min;
    }

    /**
     * Kết quả trả về của use case.
     *
     * @param report  bản chụp báo cáo.
     * @param barrier barrier phiên bản để trả về header cho client.
     */
    public record Result(ReportSnapshot report, RevisionBarrier barrier) { }
}
