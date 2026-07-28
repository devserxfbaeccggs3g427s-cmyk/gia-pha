package com.familya.event.application.usecase;

import com.familya.event.application.port.in.TombstoneDomainEventCommand;
import com.familya.event.application.port.out.EventAuthRepository;
import com.familya.event.application.port.out.EventChangePublisher;
import com.familya.event.application.port.out.EventRepository;
import com.familya.event.domain.event.EventTombstoned;
import com.familya.event.domain.exception.EventNotFoundException;
import com.familya.event.domain.model.DomainEvent;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.error.StaleProjectionException;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class TombstoneDomainEventUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(TombstoneDomainEventUseCase.class);

    private final EventRepository repo;
    private final EventChangePublisher publisher;
    private final EventAuthRepository authRepo;
    private final PlatformMetrics metrics;
    private final java.util.function.Supplier<Instant> clock;

    public TombstoneDomainEventUseCase(EventRepository repo, EventChangePublisher publisher,
                                        EventAuthRepository authRepo, PlatformMetrics metrics,
                                        CreateDomainEventUseCase.Clock clock) {
        this.repo = repo;
        this.publisher = publisher;
        this.authRepo = authRepo;
        this.metrics = metrics;
        this.clock = clock::now;
    }

    @Transactional
    public void execute(TombstoneDomainEventCommand cmd) {
        metrics.mutationAccepted("event-service", "tombstoneEvent");
        DomainEvent ev = repo.findById(cmd.eventId())
                .orElseThrow(() -> new EventNotFoundException("Event " + cmd.eventId() + " not found"));
        var auth = authRepo.findAuth(ev.treeId(), cmd.actingUser())
                .orElseThrow(() -> new ForbiddenException("No auth projection tree=" + ev.treeId() + " user=" + cmd.actingUser()));
        if (auth.isRevoked() || !auth.canEdit()) {
            throw new ForbiddenException("User " + cmd.actingUser() + " cannot edit tree " + ev.treeId());
        }
        if (auth.revision() < cmd.expectedTreeRevision()) {
            throw new StaleProjectionException(
                    "Auth projection revision " + auth.revision() + " < expected " + cmd.expectedTreeRevision());
        }
        Instant now = clock.get();
        ev.tombstone(cmd.expectedVersion(), now);
        repo.update(ev);
        publisher.publish(new EventTombstoned(ev.id(), ev.treeId(), ev.version(), now));
        LOG.info("Tombstoned event id={} version={} actingUser={}", ev.id(), ev.version(), cmd.actingUser());
    }
}