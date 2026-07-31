package com.familya.search.application.usecase;

import com.familya.search.application.port.in.RestoreSearchTreeCommand;
import com.familya.search.application.port.out.SearchWatermarkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Khôi phục projection của search service sau khi Saga xoá cây thất bại.
 *
 * <p>Bản thân các bảng chiếu là dạng chỉ-đọc, sẽ được tái dựng bởi các
 * consumer domain khi sự kiện tiếp theo đến. Use case này chỉ đảo ngược
 * việc nâng watermark để các lần đọc không âm thầm che đi tài liệu của
 * cây.</p>
 */
@Service
public class RestoreSearchTreeUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(RestoreSearchTreeUseCase.class);

    private final SearchWatermarkRepository watermarks;

    /**
     * Khởi tạo use case với cổng watermark.
     *
     * @param watermarks cổng đọc/ghi watermark của search service.
     */
    public RestoreSearchTreeUseCase(SearchWatermarkRepository watermarks) {
        this.watermarks = watermarks;
    }

    /**
     * Thực thi khôi phục.
     *
     * @param cmd lệnh chứa {@code operationId} và {@code treeId}.
     * @return kết quả (hiện không có tác động đếm được, các trường mặc định {@code 0}).
     */
    @Transactional
    public Result execute(RestoreSearchTreeCommand cmd) {
        // We do not retain a per-operation snapshot, so the safest revert is
        // to refresh the watermark to "now - 1s" which forces the next read
        // to rehydrate from the source-of-truth events.
        watermarks.refreshForRestore(cmd.treeId(), Instant.now());
        LOG.info("Refreshed search watermark for tree {} operationId={}",
                cmd.treeId(), cmd.operationId());
        return new Result(0, 0L, 0L);
    }

    /**
     * Kết quả trả về của use case.
     *
     * @param affectedCount          số bản ghi "ảnh hưởng" (luôn {@code 0} vì use case
     *                               chỉ làm mới watermark).
     * @param appliedAggregateVersion phiên bản aggregate đã áp dụng ({@code 0}).
     * @param appliedEpoch            epoch đã áp dụng ({@code 0}).
     */
    public record Result(int affectedCount, long appliedAggregateVersion, long appliedEpoch) { }
}