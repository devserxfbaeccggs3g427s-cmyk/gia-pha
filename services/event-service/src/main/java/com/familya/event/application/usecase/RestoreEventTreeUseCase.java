package com.familya.event.application.usecase;

import com.familya.event.application.port.in.RestoreEventTreeCommand;
import com.familya.event.application.port.out.EventRepository;
import com.familya.event.domain.model.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class RestoreEventTreeUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(RestoreEventTreeUseCase.class);

    private final EventRepository repo;

    public RestoreEventTreeUseCase(EventRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public Result execute(RestoreEventTreeCommand cmd) {
        List<DomainEvent> all = repo.listByTree(cmd.treeId(), true);
        Instant now = Instant.now();
        long maxVersion = 0L;
        int restored = 0;
        for (DomainEvent ev : all) {
            if (!ev.isTombstoned()) continue;
            DomainEvent alive = new DomainEvent(
                    ev.id(), ev.treeId(), ev.title(), ev.description(), ev.kind(),
                    ev.startDate(), ev.endDate(), ev.recurrence(),
                    ev.primaryMemberId(), ev.additionalMemberIds(), ev.mediaRefs(),
                    ev.location(), ev.revision(), ev.createdAt(), now, null, ev.version() + 1);
            repo.update(alive);
            maxVersion = Math.max(maxVersion, alive.version());
            restored++;
        }
        LOG.info("Restored {} events on tree {} operationId={}",
                restored, cmd.treeId(), cmd.operationId());
        return new Result(restored, maxVersion, 0L);
    }

    public record Result(int affectedCount, long appliedAggregateVersion, long appliedEpoch) { }
}