package vn.giapha.research.reporting.adapter.in.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.giapha.research.reporting.application.service.MemberSearchService;
import vn.giapha.research.reporting.security.UserContextResolver;
import vn.giapha.research.reporting.web.ApiSuccess;

@RestController
@RequestMapping(path = "/api/trees/{treeExternalId}/member-search", produces = "application/json")
public class MemberSearchController {

    private final MemberSearchService search;
    private final UserContextResolver userContextResolver;

    public MemberSearchController(MemberSearchService search,
            UserContextResolver userContextResolver) {
        this.search = search;
        this.userContextResolver = userContextResolver;
    }

    @GetMapping("/search")
    ApiSuccess<Map<String, Object>> search(
            @PathVariable String treeExternalId,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "gender", required = false) String gender,
            @RequestParam(name = "generation", required = false) Integer generation,
            @RequestParam(name = "birthYear", required = false) Integer birthYear,
            @RequestParam(name = "alive", required = false) Boolean alive,
            @RequestParam(name = "location", required = false) String location,
            @RequestParam(name = "fields", required = false) List<String> fields,
            @RequestParam(name = "offset", defaultValue = "0") int offset,
            @RequestParam(name = "limit", defaultValue = "50") int limit,
            @RequestHeader(name = "X-User-Context-Token", required = false) String userContext) {
        MemberSearchService.SearchResult result = search.search(
                userContextResolver.resolve(userContext), treeExternalId,
                query == null ? "" : query, gender, generation, birthYear, alive, location,
                fields, offset, limit);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("treeKey", result.treeKey());
        body.put("total", result.total());
        body.put("hits", result.hits().stream().map(this::toMap).toList());
        body.put("queriedAt", result.queriedAt().toString());
        return ApiSuccess.ok(body);
    }

    @GetMapping("/autocomplete")
    ApiSuccess<List<String>> autocomplete(@PathVariable String treeExternalId,
            @RequestParam(name = "q") String prefix,
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestHeader(name = "X-User-Context-Token", required = false) String userContext) {
        return ApiSuccess.ok(search.autocomplete(userContextResolver.resolve(userContext),
                treeExternalId, prefix, limit));
    }

    private Map<String, Object> toMap(MemberSearchService.Hit hit) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", hit.memberExternalId());
        map.put("fullName", hit.fullName());
        map.put("nickname", hit.nickname());
        map.put("generation", hit.generation());
        map.put("alive", hit.alive());
        map.put("gender", hit.gender());
        map.put("score", hit.score());
        map.put("matchedFields", hit.matchedFields());
        return map;
    }
}
