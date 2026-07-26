package vn.giapha.research.transfer.application.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.giapha.research.transfer.support.ForbiddenException;
import vn.giapha.research.transfer.support.Hashes;
import vn.giapha.research.transfer.support.Ids;
import vn.giapha.research.transfer.support.NotFoundException;
import vn.giapha.research.transfer.support.Principal;
import vn.giapha.research.transfer.application.port.out.ArtifactJobRepository;
import vn.giapha.research.transfer.domain.model.ArtifactJob;
import vn.giapha.research.transfer.domain.model.ArtifactJobStatus;

/**
 * V2 generated-artifact job API (Task 31, Req 10.8-10.10). Synchronous
 * export requests that exceed the Task 30 budget land here as durable jobs;
 * the worker drains {@code generated_artifact_jobs} and writes the result to
 * a private Blob path with a short-lived signed URL.
 */
@Service
public class ArtifactJobService {

    private final ArtifactJobRepository jobs;

    public ArtifactJobService(ArtifactJobRepository jobs) {
        this.jobs = jobs;
    }

    @Transactional
    public ArtifactJob create(String treeExternalId, Principal principal, String operation,
            Map<String, Object> options) {
        byte[] optionBytes = canonicalize(options).getBytes(StandardCharsets.UTF_8);
        byte[] optionHash = Hashes.sha256(optionBytes);
        return jobs.create(new ArtifactJob(0,
                Ids.newId(),
                treeExternalId,
                principal == null ? 0L : principal.userId().hashCode(),
                operation,
                optionHash,
                options == null ? Map.of() : new LinkedHashMap<>(options),
                ArtifactJobStatus.PENDING,
                0,
                false,
                 null,
                 null,
                 null,
                 null,
                 null,
                 1L,
                Instant.now(),
                Instant.now()));
    }

    @Transactional
    public ArtifactJob cancel(String externalId, Principal principal, Instant now) {
        ArtifactJob job = jobs.findByExternalId(externalId)
                .orElseThrow(() -> new NotFoundException("ARTIFACT_JOB_NOT_FOUND",
                        "Artifact job does not exist"));
        if (job.ownerUserKey() != principal.userId().hashCode()) {
            throw new ForbiddenException(
                    "Only the owner can cancel the job");
        }
        jobs.cancel(job.artifactJobKey(), now);
        return jobs.findByKey(job.artifactJobKey()).orElseThrow();
    }

    public ArtifactJob get(String externalId, Principal principal) {
        ArtifactJob job = jobs.findByExternalId(externalId)
                .orElseThrow(() -> new NotFoundException("ARTIFACT_JOB_NOT_FOUND",
                        "Artifact job does not exist"));
        if (principal == null || job.ownerUserKey() != principal.userId().hashCode()) {
            throw new ForbiddenException(
                    "Only the owner can inspect the job");
        }
        return job;
    }

    private static String canonicalize(Map<String, Object> options) {
        if (options == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        options.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sb.append(entry.getKey()).append('=')
                        .append(String.valueOf(entry.getValue())).append(';'));
        return sb.toString();
    }

    public List<ArtifactJobStatus> visibleStatuses() {
        return List.of(ArtifactJobStatus.values());
    }
}
