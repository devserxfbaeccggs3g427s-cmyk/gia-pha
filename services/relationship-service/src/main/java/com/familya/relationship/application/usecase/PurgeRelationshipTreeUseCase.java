package com.familya.relationship.application.usecase;

import com.familya.relationship.application.port.in.PurgeRelationshipTreeCommand;
import com.familya.relationship.application.port.out.RelationshipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class PurgeRelationshipTreeUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(PurgeRelationshipTreeUseCase.class);

    private final RelationshipRepository repo;

    public PurgeRelationshipTreeUseCase(RelationshipRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public Result execute(PurgeRelationshipTreeCommand cmd) {
        Instant now = Instant.now();
        int affected = repo.bulkTombstoneByTree(cmd.treeId(), now);
        LOG.info("Bulk-tombstoned {} relationships on tree {} operationId={}",
                affected, cmd.treeId(), cmd.operationId());
        return new Result(affected, Math.max(0L, cmd.targetAggregateVersion()), cmd.targetEpoch());
    }

    public record Result(int affectedCount, long appliedAggregateVersion, long appliedEpoch) { }
}