package com.familya.relationship.application.usecase;

import com.familya.relationship.application.port.in.RestoreRelationshipTreeCommand;
import com.familya.relationship.application.port.out.RelationshipRepository;
import com.familya.relationship.domain.model.Relationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class RestoreRelationshipTreeUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(RestoreRelationshipTreeUseCase.class);

    private final RelationshipRepository repo;

    public RestoreRelationshipTreeUseCase(RelationshipRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public Result execute(RestoreRelationshipTreeCommand cmd) {
        List<Relationship> all = repo.listByTree(cmd.treeId(), true);
        Instant now = Instant.now();
        long maxVersion = 0L;
        int restored = 0;
        for (Relationship r : all) {
            if (!r.tombstonedAt().equals(now) && r.tombstonedAt() != null) {
                repo.untombstone(r.id(), now, r.version());
                maxVersion = Math.max(maxVersion, r.version() + 1);
                restored++;
            }
        }
        LOG.info("Restored {} relationship edges on tree {} operationId={}",
                restored, cmd.treeId(), cmd.operationId());
        return new Result(restored, maxVersion, 0L);
    }

    public record Result(int affectedCount, long appliedAggregateVersion, long appliedEpoch) { }
}