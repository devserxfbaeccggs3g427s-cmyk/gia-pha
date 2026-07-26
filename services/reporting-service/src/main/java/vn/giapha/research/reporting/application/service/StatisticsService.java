package vn.giapha.research.reporting.application.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.giapha.research.reporting.application.port.out.TreeAccessPort;
import vn.giapha.research.reporting.application.port.out.TreeStatsRepository;
import vn.giapha.research.reporting.security.Principal;

@Service
public class StatisticsService {

    private final TreeAccessPort treeAccess;
    private final TreeStatsRepository repository;
    private final ArtifactJobLink artifactJobs;

    public StatisticsService(TreeAccessPort treeAccess,
            TreeStatsRepository repository, ArtifactJobLink artifactJobs) {
        this.treeAccess = treeAccess;
        this.repository = repository;
        this.artifactJobs = artifactJobs;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> wholeTreeStats(Principal principal, String treeExternalId,
            Instant now) {
        long treeKey = treeAccess.authorizeAndResolve(treeExternalId, principal);
        Map<String, Long> demographics = repository.demographics(treeKey);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("treeKey", treeKey);
        body.put("generatedAt", now.toString());
        body.put("totalMembers", demographics.getOrDefault("total", 0L));
        body.put("aliveMembers", demographics.getOrDefault("alive", 0L));
        body.put("deceasedMembers", demographics.getOrDefault("deceased", 0L));
        body.put("byGender", demographics);
        body.put("timeline", repository.timeline(treeKey, now));
        body.put("jobId", artifactJobs.currentTreeJob(treeKey, "REPORT"));
        return body;
    }

    public interface ArtifactJobLink {
        String currentTreeJob(long treeKey, String operation);
    }
}
