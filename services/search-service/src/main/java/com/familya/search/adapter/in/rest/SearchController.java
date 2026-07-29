package com.familya.search.adapter.in.rest;

import com.familya.search.application.port.in.AutocompleteQuery;
import com.familya.search.application.port.in.ReportQuery;
import com.familya.search.application.port.in.SearchEventsQuery;
import com.familya.search.application.port.in.SearchMediaQuery;
import com.familya.search.application.port.in.SearchMembersQuery;
import com.familya.search.application.port.in.StatisticsQuery;
import com.familya.search.application.usecase.AutocompleteUseCase;
import com.familya.search.application.usecase.ComputeReportUseCase;
import com.familya.search.application.usecase.ComputeStatisticsUseCase;
import com.familya.search.application.usecase.SearchEventsUseCase;
import com.familya.search.application.usecase.SearchMediaUseCase;
import com.familya.search.application.usecase.SearchMembersUseCase;
import com.familya.search.domain.model.AutocompleteEntry;
import com.familya.search.domain.model.EventSearchDocument;
import com.familya.search.domain.model.MediaSearchDocument;
import com.familya.search.domain.model.MemberSearchDocument;
import com.familya.search.domain.model.ReportSnapshot;
import com.familya.search.domain.model.RevisionBarrier;
import com.familya.search.domain.model.StatisticsSnapshot;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/v2/search", produces = MediaType.APPLICATION_JSON_VALUE)
public class SearchController {

    private final SearchMembersUseCase searchMembers;
    private final SearchEventsUseCase searchEvents;
    private final SearchMediaUseCase searchMedia;
    private final AutocompleteUseCase autocomplete;
    private final ComputeStatisticsUseCase statistics;
    private final ComputeReportUseCase report;

    public SearchController(SearchMembersUseCase searchMembers, SearchEventsUseCase searchEvents,
                             SearchMediaUseCase searchMedia, AutocompleteUseCase autocomplete,
                             ComputeStatisticsUseCase statistics, ComputeReportUseCase report) {
        this.searchMembers = searchMembers;
        this.searchEvents = searchEvents;
        this.searchMedia = searchMedia;
        this.autocomplete = autocomplete;
        this.statistics = statistics;
        this.report = report;
    }

    @GetMapping("/members")
    public ResponseEntity<SearchMembersUseCase.Result> members(@RequestHeader("X-Acting-User") UUID actingUser,
                                                                @RequestParam UUID treeId,
                                                                @RequestHeader(value = "X-Tree-Revision", required = false) Long expectedTreeRevision,
                                                                @RequestParam(value = "q", required = false) String q,
                                                                @RequestParam(value = "birthYear", required = false) Integer birthYear,
                                                                @RequestParam(value = "birthYearFrom", required = false) Integer birthYearFrom,
                                                                @RequestParam(value = "birthYearTo", required = false) Integer birthYearTo,
                                                                @RequestParam(value = "tombstoned", required = false) Boolean tombstoned,
                                                                @RequestParam(value = "limit", required = false) Integer limit) {
        long er = expectedTreeRevision == null ? 0L : expectedTreeRevision;
        var result = searchMembers.execute(new SearchMembersQuery(treeId, actingUser, er, q,
                birthYear, birthYearFrom, birthYearTo, tombstoned, limit));
        return withBarriers(result, result.barrier());
    }

    @GetMapping("/events")
    public ResponseEntity<SearchEventsUseCase.Result> events(@RequestHeader("X-Acting-User") UUID actingUser,
                                                              @RequestParam UUID treeId,
                                                              @RequestHeader(value = "X-Tree-Revision", required = false) Long expectedTreeRevision,
                                                              @RequestParam(value = "q", required = false) String q,
                                                              @RequestParam(value = "kind", required = false) String kind,
                                                              @RequestParam(value = "from", required = false) String from,
                                                              @RequestParam(value = "to", required = false) String to,
                                                              @RequestParam(value = "tombstoned", required = false) Boolean tombstoned,
                                                              @RequestParam(value = "limit", required = false) Integer limit) {
        long er = expectedTreeRevision == null ? 0L : expectedTreeRevision;
        var result = searchEvents.execute(new SearchEventsQuery(treeId, actingUser, er, q, kind,
                from == null ? null : LocalDate.parse(from),
                to == null ? null : LocalDate.parse(to),
                tombstoned, limit));
        return withBarriers(result, result.barrier());
    }

    @GetMapping("/media")
    public ResponseEntity<SearchMediaUseCase.Result> media(@RequestHeader("X-Acting-User") UUID actingUser,
                                                            @RequestParam UUID treeId,
                                                            @RequestHeader(value = "X-Tree-Revision", required = false) Long expectedTreeRevision,
                                                            @RequestParam(value = "q", required = false) String q,
                                                            @RequestParam(value = "kind", required = false) String kind,
                                                            @RequestParam(value = "tombstoned", required = false) Boolean tombstoned,
                                                            @RequestParam(value = "limit", required = false) Integer limit) {
        long er = expectedTreeRevision == null ? 0L : expectedTreeRevision;
        var result = searchMedia.execute(new SearchMediaQuery(treeId, actingUser, er, q, kind, tombstoned, limit));
        return withBarriers(result, result.barrier());
    }

    @GetMapping("/autocomplete")
    public ResponseEntity<List<AutocompleteEntry>> autocomplete(@RequestHeader("X-Acting-User") UUID actingUser,
                                                                 @RequestParam UUID treeId,
                                                                 @RequestHeader(value = "X-Tree-Revision", required = false) Long expectedTreeRevision,
                                                                 @RequestParam("prefix") String prefix,
                                                                 @RequestParam(value = "limit", required = false) Integer limit) {
        long er = expectedTreeRevision == null ? 0L : expectedTreeRevision;
        return ResponseEntity.ok(autocomplete.execute(new AutocompleteQuery(treeId, actingUser, er, prefix, limit)));
    }

    @GetMapping("/statistics")
    public ResponseEntity<ComputeStatisticsUseCase.Result> statistics(@RequestHeader("X-Acting-User") UUID actingUser,
                                                                       @RequestParam UUID treeId,
                                                                       @RequestHeader(value = "X-Tree-Revision", required = false) Long expectedTreeRevision,
                                                                       @RequestParam(value = "watermark", required = false) Long watermark) {
        long er = expectedTreeRevision == null ? 0L : expectedTreeRevision;
        var result = statistics.execute(new StatisticsQuery(treeId, actingUser, er, watermark));
        return withBarriers(result, result.barrier());
    }

    @GetMapping("/reports/{kind}")
    public ResponseEntity<ComputeReportUseCase.Result> report(@RequestHeader("X-Acting-User") UUID actingUser,
                                                                @RequestParam UUID treeId,
                                                                @PathVariable String kind,
                                                                @RequestHeader(value = "X-Tree-Revision", required = false) Long expectedTreeRevision,
                                                                @RequestParam(value = "watermark", required = false) Long watermark) {
        long er = expectedTreeRevision == null ? 0L : expectedTreeRevision;
        var result = report.execute(new ReportQuery(treeId, actingUser, er, kind, watermark));
        return withBarriers(result, result.barrier());
    }

    private <T> ResponseEntity<T> withBarriers(T body, RevisionBarrier barrier) {
        return ResponseEntity.ok()
                .header("X-Revision-Watermarks", String.valueOf(barrier.values()))
                .body(body);
    }
}
