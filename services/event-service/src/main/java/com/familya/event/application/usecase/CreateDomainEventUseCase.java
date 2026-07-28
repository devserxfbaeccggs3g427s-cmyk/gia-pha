package com.familya.event.application.usecase;

import com.familya.event.application.port.in.CreateDomainEventCommand;
import com.familya.event.application.port.out.EventAuthRepository;
import com.familya.event.application.port.out.EventChangePublisher;
import com.familya.event.application.port.out.EventRepository;
import com.familya.event.application.port.out.ReferenceAvailability;
import com.familya.event.domain.event.EventCreated;
import com.familya.event.domain.exception.DanglingReferenceException;
import com.familya.event.domain.model.DomainEvent;
import com.familya.event.domain.model.RecurrenceRule;
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

/**
 * Creates a domain event. Authorisation from the local membership
 * projection (deny on absent or stale). References to members and
 * media are validated against the local projections; missing rows
 * raise {@link DanglingReferenceException}. Leap-day recurrence
 * anchors use February 28 fallback (legacy).
 */
@Service
public class CreateDomainEventUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(CreateDomainEventUseCase.class);

    private final EventRepository repo;
    private final EventChangePublisher publisher;
    private final EventAuthRepository authRepo;
    private final ReferenceAvailability refs;
    private final PlatformMetrics metrics;
    private final Clock clock;

    public CreateDomainEventUseCase(EventRepository repo, EventChangePublisher publisher,
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
    public UUID execute(CreateDomainEventCommand cmd) {
        metrics.mutationAccepted("event-service", "createEvent");
        var auth = authRepo.findAuth(cmd.treeId(), cmd.actingUser())
                .orElseThrow(() -> new ForbiddenException("No auth projection tree=" + cmd.treeId() + " user=" + cmd.actingUser()));
        if (auth.isRevoked() || !auth.canEdit()) {
            throw new ForbiddenException("User " + cmd.actingUser() + " cannot edit tree " + cmd.treeId());
        }
        if (auth.revision() < cmd.expectedTreeRevision()) {
            throw new StaleProjectionException(
                    "Auth projection revision " + auth.revision() + " < expected " + cmd.expectedTreeRevision());
        }
        validateReferences(cmd.treeId(), cmd.primaryMemberId(), cmd.additionalMemberIds(), cmd.mediaRefs(), refs);

        Instant now = clock.now();
        // Leap-day recurrence: snap the anchor to Feb 28 in non-leap years.
        java.time.LocalDate start = cmd.startDate();
        if (start != null && cmd.recurrence() != null) {
            start = RecurrenceRule.applyLeapDay(start, start);
        }
        UUID id = UUID.randomUUID();
        DomainEvent ev = new DomainEvent(
                id, cmd.treeId(), cmd.title(), cmd.description(), cmd.kind(),
                start, cmd.endDate(), cmd.recurrence(),
                cmd.primaryMemberId(),
                cmd.additionalMemberIds() == null ? java.util.List.of() : cmd.additionalMemberIds(),
                cmd.mediaRefs() == null ? java.util.List.of() : cmd.mediaRefs(),
                cmd.location(),
                1L, now, now, null, 0L);
        repo.insert(ev);
        publisher.publish(new EventCreated(id, cmd.treeId(), cmd.kind(), cmd.title(), ev.version(), now));
        LOG.info("Created event id={} tree={} actingUser={}", id, cmd.treeId(), cmd.actingUser());
        return id;
    }

    private static void validateReferences(UUID treeId, UUID primary, java.util.List<UUID> additional,
                                            java.util.List<UUID> media, ReferenceAvailability refs) {
        Set<UUID> dangling = new HashSet<>();
        if (primary != null && !refs.isMemberAvailable(treeId, primary)) dangling.add(primary);
        if (additional != null) {
            for (UUID m : additional) {
                if (m != null && !refs.isMemberAvailable(treeId, m)) dangling.add(m);
            }
        }
        if (media != null) {
            for (UUID m : media) {
                if (m != null && !refs.isMediaAvailable(treeId, m)) dangling.add(m);
            }
        }
        if (!dangling.isEmpty()) {
            throw new DanglingReferenceException(
                    "Dangling references for tree=" + treeId + ": " + dangling);
        }
    }

    public interface Clock { Instant now(); }
}