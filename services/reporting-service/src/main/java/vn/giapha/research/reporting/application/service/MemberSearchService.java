package vn.giapha.research.reporting.application.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import vn.giapha.research.reporting.application.port.out.MemberSearchRepository;
import vn.giapha.research.reporting.application.port.out.TreeAccessPort;
import vn.giapha.research.reporting.security.Principal;

@Service
public class MemberSearchService {

    private static final int MAX_PAGE_SIZE = 100;

    private final MemberSearchRepository repository;
    private final TreeAccessPort treeAccess;

    public MemberSearchService(MemberSearchRepository repository, TreeAccessPort treeAccess) {
        this.repository = repository;
        this.treeAccess = treeAccess;
    }

    public record SearchResult(long treeKey, int total, List<Hit> hits,
            Instant queriedAt) {
    }

    public record Hit(String memberExternalId, String fullName, String nickname,
            Integer generation, Boolean alive, String gender, double score,
            List<String> matchedFields) {
    }

    @Transactional(readOnly = true)
    public SearchResult search(Principal principal, String treeExternalId, String query,
            String gender, Integer generation, Integer birthYear,
            Boolean alive, String location, List<String> selectedFields,
            int offset, int limit) {
        validatePage(offset, limit);
        long treeKey = treeAccess.authorizeAndResolve(treeExternalId, principal);
        String normalized = VietnameseSearchNormalizer.normalize(query);
        List<String> fields = selectedFields == null || selectedFields.isEmpty()
                ? List.of("fullName", "nickname", "occupation", "placeOfBirth")
                : List.copyOf(selectedFields);
        List<MemberSearchRepository.SearchRow> rows = repository.search(treeKey, normalized,
                gender, generation, birthYear, alive, location, fields, offset, limit);
        int total = repository.count(treeKey, normalized, gender, generation, birthYear,
                alive, location, fields);
        List<Hit> hits = rows.stream()
                .map(row -> new Hit(row.externalId(), row.fullName(), row.nickname(),
                        row.generation(), row.alive(), row.gender(), row.score(),
                        row.matchedFields()))
                .toList();
        return new SearchResult(treeKey, total, hits, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<String> autocomplete(Principal principal, String treeExternalId,
            String prefix, int limit) {
        validatePage(0, limit);
        long treeKey = treeAccess.authorizeAndResolve(treeExternalId, principal);
        return repository.autocomplete(treeKey,
                VietnameseSearchNormalizer.normalize(prefix), limit);
    }

    public long resolveTreeKey(Principal principal, String treeExternalId) {
        return treeAccess.authorizeAndResolve(treeExternalId, principal);
    }

    static double score(List<String> matchedFields, String fullName) {
        double sum = 0.0;
        for (String field : matchedFields) {
            sum += switch (field) {
                case "fullName" -> 1.0;
                case "nickname" -> 0.7;
                case "occupation" -> 0.4;
                case "placeOfBirth" -> 0.3;
                default -> 0.1;
            };
        }
        return sum + (fullName == null ? 0 : 0.001);
    }

    public Map<String, Object> debugProfile() {
        return new LinkedHashMap<>(Map.of(
                "engine", "vietnamese-normalized-substring",
                "pageSizeMax", MAX_PAGE_SIZE));
    }

    private static void validatePage(int offset, int limit) {
        if (offset < 0 || limit <= 0 || limit > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "offset must be non-negative and limit must be 1-" + MAX_PAGE_SIZE);
        }
    }
}
