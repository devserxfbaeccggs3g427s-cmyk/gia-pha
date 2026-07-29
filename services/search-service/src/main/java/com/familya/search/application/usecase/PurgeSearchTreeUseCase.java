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
 * Participant step for delete-tree Saga. Removes all tree-scoped documents
 * from member/event/media search projections, autocomplete, statistics, and
 * report caches, then advances the local watermark to the target
 * aggregate version/epoch so subsequent reads see the converged state.
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

    @Transactional
    public Result execute(PurgeSearchTreeCommand cmd) {
        long memberCount = members.deleteByTree(cmd.treeId());
        long eventCount = events.deleteByTree(cmd.treeId());
        long mediaCount = media.deleteByTree(cmd.treeId());
        long autocompleteCount = autocomplete.deleteByTree(cmd.treeId());
        long statsCount = statistics.deleteByTree(cmd.treeId());
        long reportCount = reports.deleteByTree(cmd.treeId());
        watermarks.advance(cmd.treeId(), cmd.targetAggregateVersion(),
                cmd.targetEpoch(), Instant.now());

        long total = memberCount + eventCount + mediaCount
                + autocompleteCount + statsCount + reportCount;
        LOG.info("Purged {} search projections on tree {} operationId={}",
                total, cmd.treeId(), cmd.operationId());
        return new Result((int) total, cmd.targetAggregateVersion(), cmd.targetEpoch());
    }

    public record Result(int affectedCount, long appliedAggregateVersion, long appliedEpoch) { }
}