package com.familya.search.application.usecase;

import com.familya.search.application.port.in.SearchEventsQuery;
import com.familya.search.application.port.out.EventSearchRepository;
import com.familya.search.application.port.out.SearchAuthorization;
import com.familya.search.application.port.out.SearchWatermarkRepository;
import com.familya.search.domain.model.EventSearchDocument;
import com.familya.search.domain.model.RevisionBarrier;
import com.familya.search.domain.normalizer.VietnameseNormalizer;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.telemetry.PlatformMetrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Use case tìm kiếm sự kiện trong phạm vi một cây gia phả.
 *
 * <p>Luồng xử lý:</p>
 * <ol>
 *   <li>Kiểm tra quyền truy cập.</li>
 *   <li>Chuẩn hoá chuỗi truy vấn bằng {@code VietnameseNormalizer}.</li>
 *   <li>Áp giới hạn an toàn (mặc định 50, tối đa 500).</li>
 *   <li>Đóng gói bộ lọc và uỷ quyền cho {@code EventSearchRepository} truy vấn.</li>
 *   <li>Trả kèm barrier để client biết mức hội tụ hiện tại.</li>
 * </ol>
 */
@Service
public class SearchEventsUseCase {

    private final SearchAuthorization authz;
    private final EventSearchRepository repo;
    private final SearchWatermarkRepository watermark;
    private final PlatformMetrics metrics;

    /**
     * Khởi tạo use case với các phụ thuộc bắt buộc.
     *
     * @param authz     cổng kiểm tra quyền.
     * @param repo      cổng truy vấn tài liệu sự kiện.
     * @param watermark cổng đọc barrier phiên bản.
     * @param metrics   cổng ghi nhận telemetry.
     */
    public SearchEventsUseCase(SearchAuthorization authz, EventSearchRepository repo,
                                 SearchWatermarkRepository watermark, PlatformMetrics metrics) {
        this.authz = authz;
        this.repo = repo;
        this.watermark = watermark;
        this.metrics = metrics;
    }

    /**
     * Thực thi truy vấn.
     *
     * @param q truy vấn chứa cây, người dùng, phiên bản, chuỗi tìm kiếm,
     *          bộ lọc (loại, khoảng ngày, tombstoned) và giới hạn.
     * @return kết quả gồm danh sách tài liệu và barrier.
     * @throws ForbiddenException nếu client không có quyền.
     */
    @Transactional(readOnly = true)
    public Result execute(SearchEventsQuery q) {
        SearchAuthorization.Decision d = authz.authorize(q.treeId(), q.actingUser(), q.expectedTreeRevision());
        if (!d.isAllowed()) {
            throw new ForbiddenException("Cannot search events: " + d.reason());
        }
        String normalized = VietnameseNormalizer.normalize(q.q());
        // Áp giới hạn an toàn: nếu client không truyền limit thì mặc định 50,
        // nếu truyền thì cắt về tối đa 500 để tránh truy vấn quá nặng.
        int limit = q.limit() == null ? 50 : Math.min(q.limit(), 500);
        var filter = new com.familya.search.application.port.in.SearchEventsCommand.EventFilter(
                q.kind(), q.from(), q.to(), q.tombstoned());
        List<EventSearchDocument> docs = repo.search(filter, normalized, q.treeId(), limit);
        metrics.mutationAccepted("search-service", "searchEvents");
        return new Result(docs, watermark.barrierFor(q.treeId()));
    }

    /**
     * Kết quả trả về của use case.
     *
     * @param docs    danh sách tài liệu sự kiện khớp truy vấn.
     * @param barrier barrier phiên bản để trả về header cho client.
     */
    public record Result(List<EventSearchDocument> docs, RevisionBarrier barrier) { }
}
