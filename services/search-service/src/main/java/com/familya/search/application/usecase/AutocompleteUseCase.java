package com.familya.search.application.usecase;

import com.familya.search.application.port.in.AutocompleteQuery;
import com.familya.search.application.port.out.AutocompleteRepository;
import com.familya.search.application.port.out.SearchAuthorization;
import com.familya.search.domain.model.AutocompleteEntry;
import com.familya.search.domain.normalizer.VietnameseNormalizer;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.telemetry.PlatformMetrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Use case cung cấp gợi ý (autocomplete) cho người dùng khi họ gõ vào ô tìm
 * kiếm trong phạm vi một cây gia phả.
 *
 * <p>Luồng xử lý:</p>
 * <ol>
 *   <li>Kiểm tra quyền truy cập bằng {@code SearchAuthorization}.</li>
 *   <li>Chuẩn hoá tiền tố bằng {@code VietnameseNormalizer} để so khớp
 *       không phân biệt dấu.</li>
 *   <li>Áp giới hạn kết quả an toàn (mặc định 20, tối đa 100) để tránh
 *       truy vấn trả về quá nhiều bản ghi.</li>
 *   <li>Ghi nhận metric và trả kết quả.</li>
 * </ol>
 */
@Service
public class AutocompleteUseCase {

    private final SearchAuthorization authz;
    private final AutocompleteRepository repo;
    private final PlatformMetrics metrics;

    /**
     * Khởi tạo use case với các phụ thuộc bắt buộc.
     *
     * @param authz   cổng kiểm tra quyền.
     * @param repo    cổng đọc bảng gợi ý.
     * @param metrics cổng ghi nhận telemetry nền tảng.
     */
    public AutocompleteUseCase(SearchAuthorization authz, AutocompleteRepository repo, PlatformMetrics metrics) {
        this.authz = authz;
        this.repo = repo;
        this.metrics = metrics;
    }

    /**
     * Thực thi truy vấn gợi ý.
     *
     * @param q truy vấn chứa {@code treeId}, {@code actingUser},
     *          {@code expectedTreeRevision}, tiền tố và giới hạn.
     * @return danh sách gợi ý đã sắp xếp theo trọng số giảm dần.
     * @throws ForbiddenException nếu client không có quyền truy cập.
     */
    @Transactional(readOnly = true)
    public List<AutocompleteEntry> execute(AutocompleteQuery q) {
        // Bước 1: Hỏi projection ủy quyền xem client có được phép tìm trong
        // cây này không. Mọi trạng thái khác ALLOW đều bị từ chối.
        SearchAuthorization.Decision d = authz.authorize(q.treeId(), q.actingUser(), q.expectedTreeRevision());
        if (!d.isAllowed()) {
            throw new ForbiddenException("Cannot autocomplete: " + d.reason());
        }
        // Bước 2: Áp giới hạn an toàn. Nếu client không truyền limit thì
        // dùng mặc định 20; nếu truyền thì cắt về tối đa 100 để tránh
        // truy vấn quá nặng.
        int limit = q.limit() == null ? 20 : Math.min(q.limit(), 100);
        metrics.mutationAccepted("search-service", "autocomplete");
        // Bước 3: Chuẩn hoá tiền tố rồi truy vấn bảng autocomplete.
        return repo.suggestions(VietnameseNormalizer.normalize(q.prefix()), q.treeId(), limit);
    }
}
