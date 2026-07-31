package com.familya.search.application.usecase;

import com.familya.search.application.port.in.SearchMediaQuery;
import com.familya.search.application.port.out.MediaSearchRepository;
import com.familya.search.application.port.out.SearchAuthorization;
import com.familya.search.application.port.out.SearchWatermarkRepository;
import com.familya.search.domain.model.MediaSearchDocument;
import com.familya.search.domain.model.RevisionBarrier;
import com.familya.search.domain.normalizer.VietnameseNormalizer;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.telemetry.PlatformMetrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Use case tìm kiếm media trong phạm vi một cây gia phả.
 *
 * <p>Tương tự {@code SearchEventsUseCase}: kiểm tra quyền, chuẩn hoá chuỗi,
 * giới hạn kết quả, uỷ quyền truy vấn và trả về kèm barrier.</p>
 */
@Service
public class SearchMediaUseCase {

    private final SearchAuthorization authz;
    private final MediaSearchRepository repo;
    private final SearchWatermarkRepository watermark;
    private final PlatformMetrics metrics;

    /**
     * Khởi tạo use case với các phụ thuộc bắt buộc.
     *
     * @param authz     cổng kiểm tra quyền.
     * @param repo      cổng truy vấn tài liệu media.
     * @param watermark cổng đọc barrier phiên bản.
     * @param metrics   cổng ghi nhận telemetry.
     */
    public SearchMediaUseCase(SearchAuthorization authz, MediaSearchRepository repo,
                                SearchWatermarkRepository watermark, PlatformMetrics metrics) {
        this.authz = authz;
        this.repo = repo;
        this.watermark = watermark;
        this.metrics = metrics;
    }

    /**
     * Thực thi truy vấn media.
     *
     * @param q truy vấn chứa cây, người dùng, phiên bản, chuỗi tìm kiếm,
     *          bộ lọc (loại, tombstoned) và giới hạn.
     * @return kết quả gồm danh sách tài liệu và barrier.
     * @throws ForbiddenException nếu client không có quyền.
     */
    @Transactional(readOnly = true)
    public Result execute(SearchMediaQuery q) {
        SearchAuthorization.Decision d = authz.authorize(q.treeId(), q.actingUser(), q.expectedTreeRevision());
        if (!d.isAllowed()) {
            throw new ForbiddenException("Cannot search media: " + d.reason());
        }
        String normalized = VietnameseNormalizer.normalize(q.q());
        // Áp giới hạn an toàn tương tự SearchEventsUseCase.
        int limit = q.limit() == null ? 50 : Math.min(q.limit(), 500);
        var filter = new com.familya.search.application.port.in.SearchMediaCommand.MediaFilter(q.kind(), q.tombstoned());
        List<MediaSearchDocument> docs = repo.search(filter, normalized, q.treeId(), limit);
        metrics.mutationAccepted("search-service", "searchMedia");
        return new Result(docs, watermark.barrierFor(q.treeId()));
    }

    /**
     * Kết quả trả về của use case.
     *
     * @param docs    danh sách tài liệu media khớp truy vấn.
     * @param barrier barrier phiên bản để trả về header cho client.
     */
    public record Result(List<MediaSearchDocument> docs, RevisionBarrier barrier) { }
}
