package com.familya.event.application.usecase;

import com.familya.event.application.port.in.TombstoneDomainEventCommand;
import com.familya.event.application.port.in.UpdateDomainEventCommand;
import com.familya.event.application.port.out.EventAuthRepository;
import com.familya.event.application.port.out.EventChangePublisher;
import com.familya.event.application.port.out.EventRepository;
import com.familya.event.application.port.out.ReferenceAvailability;
import com.familya.event.domain.event.EventCreated;
import com.familya.event.domain.event.EventTombstoned;
import com.familya.event.domain.exception.DanglingReferenceException;
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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class UpdateDomainEventUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(UpdateDomainEventUseCase.class);

    private final EventRepository repo;
    private final EventChangePublisher publisher;
    private final EventAuthRepository authRepo;
    private final ReferenceAvailability refs;
    private final PlatformMetrics metrics;
    private final Clock clock;

    public UpdateDomainEventUseCase(EventRepository repo, EventChangePublisher publisher,
                                     EventAuthRepository authRepo, ReferenceAvailability refs,
                                     PlatformMetrics metrics, Clock clock) {
        this.repo = repo;
        this.publisher = publisher;
        this.authRepo = authRepo;
        this.refs = refs;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public void execute(UpdateDomainEventCommand cmd) {
        metrics.mutationAccepted("event-service", "updateEvent");
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
        Set<UUID> dangling = new HashSet<>();
        if (cmd.primaryMemberId() != null && !refs.isMemberAvailable(ev.treeId(), cmd.primaryMemberId())) {
            dangling.add(cmd.primaryMemberId());
        }
        if (cmd.additionalMemberIds() != null) {
            for (UUID m : cmd.additionalMemberIds()) {
                if (m != null && !refs.isMemberAvailable(ev.treeId(), m)) dangling.add(m);
            }
        }
        if (cmd.mediaRefs() != null) {
            for (UUID m : cmd.mediaRefs()) {
                if (m != null && !refs.isMediaAvailable(ev.treeId(), m)) dangling.add(m);
            }
        }
        if (!dangling.isEmpty()) {
            throw new DanglingReferenceException(
                    "Dangling references for tree=" + ev.treeId() + ": " + dangling);
        }
        Instant now = clock.now();
        ev.update(cmd.title(), cmd.description(), cmd.kind(),
                cmd.startDate(), cmd.endDate(), cmd.recurrence(),
                cmd.primaryMemberId(),
                cmd.additionalMemberIds() == null ? java.util.List.of() : cmd.additionalMemberIds(),
                cmd.mediaRefs() == null ? java.util.List.of() : cmd.mediaRefs(),
                cmd.location(),
                cmd.expectedVersion(), now);
        repo.update(ev);
        publisher.publish(new EventCreated(ev.id(), ev.treeId(), ev.kind(), ev.title(), ev.version(), now));
        LOG.info("Updated event id={} version={} actingUser={}", ev.id(), ev.version(), cmd.actingUser());
    }

    public interface Clock { Instant now(); }
}