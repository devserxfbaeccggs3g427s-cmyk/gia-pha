package com.familya.search.application.usecase;

import com.familya.search.application.port.in.PurgeSearchTreeCommand;
import com.familya.search.application.port.out.AutocompleteRepository;
import com.familya.search.application.port.out.EventSearchRepository;
import com.familya.search.application.port.out.MediaSearchRepository;
import com.familya.search.application.port.out.MemberSearchRepository;
import com.familya.search.application.port.out.ReportRepository;
import com.familya.search.application.port.out.SearchWatermarkRepository;
import com.familya.search.application.port.out.StatisticsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Bước tham gia (participant) của Saga xoá cây.
 *
 * <p>Xoá toàn bộ tài liệu trong các bảng chiếu của search service
 * (member/event/media/autocomplete/statistics/report) cho cây đã chỉ định,
 * sau đó nâng watermark cục bộ lên mức phiên bản/epoch mục tiêu để các lần
 * đọc sau nhìn thấy trạng thái đã hội tụ.</p>
 *
 * <p>Mọi thao tác xoá và nâng watermark được chạy trong một transaction
 * duy nhất - đảm bảo hoặc tất cả thành công hoặc không bước nào được áp
 * dụng.</p>
 */
@Service
public class PurgeSearchTreeUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(PurgeSearchTreeUseCase.class);

    private final MemberSearchRepository members;
    private final EventSearchRepository events;
    private final MediaSearchRepository media;
    private final AutocompleteRepository autocomplete;
    private final StatisticsRepository statistics;
    private final ReportRepository reports;
    private final SearchWatermarkRepository watermarks;

    /**
     * Khởi tạo use case với các cổng persistence tương ứng với các bảng chiếu
     * mà Saga cần xoá.
     */
    public PurgeSearchTreeUseCase(MemberSearchRepository members,
                                  EventSearchRepository events,
                                  MediaSearchRepository media,
                                  AutocompleteRepository autocomplete,
                                  StatisticsRepository statistics,
                                  ReportRepository reports,
                                  SearchWatermarkRepository watermarks) {
        this.members = members;
        this.events = events;
        this.media = media;
        this.autocomplete = autocomplete;
        this.statistics = statistics;
        this.reports = reports;
        this.watermarks = watermarks;
    }

    /**
     * Thực thi xoá sạch dữ liệu của một cây và nâng watermark.
     *
     * @param cmd lệnh chứa {@code operationId}, {@code treeId}, phiên bản
     *            và epoch mục tiêu.
     * @return kết quả gồm tổng số bản ghi đã xoá và phiên bản/epoch đã áp dụng.
     */
    @Transactional
    public Result execute(PurgeSearchTreeCommand cmd) {
        // Xoá tuần tự từng bảng để thu số liệu cho log; nếu một bảng không
        // có triển khai deleteByTree thật thì default trả về 0L.
        long memberCount = members.deleteByTree(cmd.treeId());
        long eventCount = events.deleteByTree(cmd.treeId());
        long mediaCount = media.deleteByTree(cmd.treeId());
        long autocompleteCount = autocomplete.deleteByTree(cmd.treeId());
        long statsCount = statistics.deleteByTree(cmd.treeId());
        long reportCount = reports.deleteByTree(cmd.treeId());
        // Nâng watermark cho mọi miền lên phiên bản mục tiêu - việc này
        // đảm bảo các lần đọc sau thấy "trạng thái mới" ngay cả khi chưa
        // nhận thêm sự kiện nào.
        watermarks.advance(cmd.treeId(), cmd.targetAggregateVersion(),
                cmd.targetEpoch(), Instant.now());

        long total = memberCount + eventCount + mediaCount
                + autocompleteCount + statsCount + reportCount;
        LOG.info("Purged {} search projections on tree {} operationId={}",
                total, cmd.treeId(), cmd.operationId());
        return new Result((int) total, cmd.targetAggregateVersion(), cmd.targetEpoch());
    }

    /**
     * Kết quả trả về của use case.
     *
     * @param affectedCount          tổng số bản ghi đã xoá.
     * @param appliedAggregateVersion phiên bản aggregate đã áp dụng.
     * @param appliedEpoch            epoch đã áp dụng.
     */
    public record Result(int affectedCount, long appliedAggregateVersion, long appliedEpoch) { }
}