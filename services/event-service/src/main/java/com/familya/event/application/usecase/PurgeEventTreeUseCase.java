package com.familya.event.application.usecase;

import com.familya.event.application.port.in.PurgeEventTreeCommand;
import com.familya.event.application.port.out.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class PurgeEventTreeUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(PurgeEventTreeUseCase.class);

    private final EventRepository repo;

    public PurgeEventTreeUseCase(EventRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public Result execute(PurgeEventTreeCommand cmd) {
        Instant now = Instant.now();
        int affected = repo.bulkTombstoneByTree(cmd.treeId(), now);
        LOG.info("Bulk-tombstoned {} events on tree {} operationId={}",
                affected, cmd.treeId(), cmd.operationId());
        return new Result(affected, Math.max(0L, cmd.targetAggregateVersion()), cmd.targetEpoch());
    }

    public record Result(int affectedCount, long appliedAggregateVersion, long appliedEpoch) { }
}