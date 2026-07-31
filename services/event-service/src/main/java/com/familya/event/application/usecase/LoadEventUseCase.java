package com.familya.event.application.usecase;

import com.familya.event.application.port.in.LoadEventCommand;
import com.familya.event.application.port.out.EventRepository;
import com.familya.event.domain.model.DomainEvent;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent migration loader for the event service.
 */
@Service
public class LoadEventUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(LoadEventUseCase.class);

    private final EventRepository repo;
    private final PlatformMetrics metrics;

    public LoadEventUseCase(EventRepository repo, PlatformMetrics metrics) {
        this.repo = repo;
        this.metrics = metrics;
    }

    @Transactional
    public LoadResult execute(LoadEventCommand cmd) {
        metrics.mutationAccepted("event-service", "loadEvent");
        if (repo.findById(cmd.eventId()).isPresent()) {
            return new LoadResult(cmd.eventId(), LoadResult.Status.DUPLICATE);
        }
        DomainEvent ev = new DomainEvent(
                cmd.eventId(), cmd.treeId(), cmd.title(), cmd.description(), cmd.kind(),
                cmd.startDate(), cmd.endDate(), cmd.recurrence(),
                cmd.primaryMemberId(),
                cmd.additionalMemberIds() == null ? java.util.List.of() : cmd.additionalMemberIds(),
                cmd.mediaRefs() == null ? java.util.List.of() : cmd.mediaRefs(),
                cmd.location(),
                1L, cmd.createdAt(), cmd.updatedAt(),
                cmd.tombstoned() ? cmd.updatedAt() : null, 0L);
        try {
            repo.insert(ev);
        } catch (DuplicateKeyException dup) {
            return new LoadResult(cmd.eventId(), LoadResult.Status.DUPLICATE);
        }
        LOG.info("Loaded event id={} tree={}", cmd.eventId(), cmd.treeId());
        return new LoadResult(cmd.eventId(), LoadResult.Status.LOADED);
    }

    public record LoadResult(java.util.UUID eventId, Status status) {
        public enum Status { LOADED, DUPLICATE }
    }
}