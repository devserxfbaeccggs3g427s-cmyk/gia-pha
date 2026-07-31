package com.familya.relationship.application.usecase;

import com.familya.relationship.application.port.in.LoadRelationshipManifestCommand;
import com.familya.relationship.application.port.out.RelationshipRepository;
import com.familya.relationship.domain.model.Relationship;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoadRelationshipManifestUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(LoadRelationshipManifestUseCase.class);

    private final RelationshipRepository repo;
    private final PlatformMetrics metrics;

    public LoadRelationshipManifestUseCase(RelationshipRepository repo, PlatformMetrics metrics) {
        this.repo = repo;
        this.metrics = metrics;
    }

    @Transactional
    public LoadResult execute(LoadRelationshipManifestCommand cmd) {
        metrics.mutationAccepted("relationship-service", "loadRelationshipManifest");
        int loaded = 0, duplicates = 0;
        for (var line : cmd.relationships()) {
            if (repo.findById(line.relationshipId()).isPresent()) {
                duplicates++;
                continue;
            }
            Relationship rel = new Relationship(
                    line.relationshipId(), cmd.treeId(), line.kind(),
                    line.fromMemberId(), line.toMemberId(), line.metadataJson(),
                    line.revision(), line.createdAt(), null, 0L);
            try {
                repo.insert(rel);
                loaded++;
            } catch (DuplicateKeyException dup) {
                duplicates++;
            }
        }
        LOG.info("Loaded relationships tree={} loaded={} duplicates={}", cmd.treeId(), loaded, duplicates);
        return new LoadResult(loaded, duplicates);
    }

    public record LoadResult(int loaded, int duplicates) { }
}