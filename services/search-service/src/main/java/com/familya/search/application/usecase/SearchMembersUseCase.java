package com.familya.search.application.usecase;

import com.familya.search.application.port.in.SearchMembersQuery;
import com.familya.search.application.port.out.MemberSearchRepository;
import com.familya.search.application.port.out.SearchAuthRepository;
import com.familya.search.application.port.out.SearchAuthorization;
import com.familya.search.application.port.out.SearchWatermarkRepository;
import com.familya.search.domain.exception.StaleBarrierException;
import com.familya.search.domain.model.MemberSearchDocument;
import com.familya.search.domain.model.RevisionBarrier;
import com.familya.search.domain.model.Watermark;
import com.familya.search.domain.normalizer.VietnameseNormalizer;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.telemetry.PlatformMetrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Use case tìm kiếm thành viên trong phạm vi một cây gia phả.
 *
 * <p>Luồng xử lý:</p>
 * <ol>
 *   <li>Kiểm tra quyền truy cập bằng {@code SearchAuthorization}.</li>
 *   <li>Chuẩn hoá chuỗi truy vấn và áp giới hạn an toàn.</li>
 *   <li>Đóng gói bộ lọc (năm sinh, khoảng năm sinh, tombstoned).</li>
 *   <li>Uỷ quyền cho {@code MemberSearchRepository} truy vấn.</li>
 *   <li>Đọc barrier để trả kèm cho client.</li>
 * </ol>
 */
@Service
public class SearchMembersUseCase {

    private final SearchAuthorization authz;
    private final MemberSearchRepository repo;
    private final SearchWatermarkRepository watermark;
    private final PlatformMetrics metrics;

    /**
     * Khởi tạo use case với các phụ thuộc bắt buộc.
     *
     * @param authz     cổng kiểm tra quyền.
     * @param repo      cổng truy vấn tài liệu thành viên.
     * @param watermark cổng đọc barrier phiên bản.
     * @param metrics   cổng ghi nhận telemetry.
     */
    public SearchMembersUseCase(SearchAuthorization authz, MemberSearchRepository repo,
                                  SearchWatermarkRepository watermark, PlatformMetrics metrics) {
        this.authz = authz;
        this.repo = repo;
        this.watermark = watermark;
        this.metrics = metrics;
    }

    /**
     * Thực thi truy vấn thành viên.
     *
     * @param q truy vấn chứa cây, người dùng, phiên bản, chuỗi tìm kiếm,
     *          bộ lọc năm sinh và giới hạn.
     * @return kết quả gồm danh sách tài liệu và barrier.
     * @throws ForbiddenException nếu client không có quyền.
     */
    @Transactional(readOnly = true)
    public Result execute(SearchMembersQuery q) {
        SearchAuthorization.Decision d = authz.authorize(q.treeId(), q.actingUser(), q.expectedTreeRevision());
        if (!d.isAllowed()) {
            throw new ForbiddenException("Cannot search members: " + d.reason());
        }
        String normalized = VietnameseNormalizer.normalize(q.q());
        // Áp giới hạn an toàn tương tự các use case tìm kiếm khác.
        int limit = q.limit() == null ? 50 : Math.min(q.limit(), 500);
        var filter = new com.familya.search.application.port.in.SearchMembersCommand.MemberFilter(
                q.birthYear(), q.birthYearFrom(), q.birthYearTo(), q.tombstoned());
        List<MemberSearchDocument> docs = repo.search(filter, normalized, q.treeId(), limit);
        RevisionBarrier barrier = watermark.barrierFor(q.treeId());
        metrics.mutationAccepted("search-service", "searchMembers");
        return new Result(docs, barrier);
    }

    /**
     * Kết quả trả về của use case.
     *
     * @param docs    danh sách tài liệu thành viên khớp truy vấn.
     * @param barrier barrier phiên bản để trả về header cho client.
     */
    public record Result(List<MemberSearchDocument> docs, RevisionBarrier barrier) { }
}
