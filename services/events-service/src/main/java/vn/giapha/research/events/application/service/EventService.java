package vn.giapha.research.events.application.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.giapha.research.events.application.port.out.OutboxPort;
import vn.giapha.research.events.support.EventSupport;
import vn.giapha.research.events.support.EventSupport.NotFoundException;
import vn.giapha.research.events.support.EventSupport.Principal;
import vn.giapha.research.events.support.EventSupport.ValidationException;
import vn.giapha.research.events.application.port.out.EventRepository;
import vn.giapha.research.events.application.port.out.FamilyTreeRepository;
import vn.giapha.research.events.application.service.TreeAuthorizationService.Action;
import vn.giapha.research.events.domain.Event;
import vn.giapha.research.events.domain.EventType;
import vn.giapha.research.events.domain.FamilyTree;

/**
 * Event lifecycle service (Task 25, Req 6). Mirrors the legacy semantics:
 *
 * <ul>
 *   <li>{@code event_members} / {@code event_media} are the only sources of
 *       truth for participating members and attached media;</li>
 *   <li>upcoming recurrence computes within 0–366 days; leap day birthdays
 *       collapse to February 28 in non-leap years;</li>
 *   <li>mutations commit event row + link rows in one transaction.</li>
 * </ul>
 */
@Service
public class EventService {

    private static final int UPCOMING_WINDOW_DAYS = 366;

    private final FamilyTreeRepository trees;
    private final EventRepository events;
    private final TreeAuthorizationService authorization;
    private final OutboxPort outbox;

    public EventService(FamilyTreeRepository trees, EventRepository events,
            TreeAuthorizationService authorization, OutboxPort outbox) {
        this.trees = trees;
        this.events = events;
        this.authorization = authorization;
        this.outbox = outbox;
    }

    @Transactional
    public Event create(Principal principal, String treeExternalId, NewEventInput input,
            Instant now) {
        FamilyTree tree = requireTree(treeExternalId);
        authorization.authorize(tree.treeKey(), principal, Action.WRITE, true);
        validateEvent(input);
        Event event = new Event(0, tree.treeKey(), EventSupport.newId(), input.type(),
                input.type() == EventType.CUSTOM ? input.customType() : null,
                input.title(), input.eventDate(), input.location(), input.description(),
                input.memberKeys(), input.mediaKeys(), 1L, now, now);
        Event inserted = events.insert(event);
        enqueueTreeMutation(tree, "EVENT_CREATED", inserted.externalId(), principal, now);
        return inserted;
    }

    @Transactional
    public Event update(Principal principal, String treeExternalId, String externalId,
            NewEventInput input, long expectedVersion, Instant now) {
        FamilyTree tree = requireTree(treeExternalId);
        authorization.authorize(tree.treeKey(), principal, Action.WRITE, true);
        Event existing = events.findByExternalId(tree.treeKey(), externalId)
                .orElseThrow(() -> new NotFoundException("EVENT_NOT_FOUND",
                        "Event does not exist"));
        if (existing.version() != expectedVersion) {
            throw new ValidationException("VERSION_CONFLICT");
        }
        validateEvent(input);
        Event updated = new Event(existing.eventKey(), existing.treeKey(), existing.externalId(),
                input.type(),
                input.type() == EventType.CUSTOM ? input.customType() : null,
                input.title(), input.eventDate(), input.location(), input.description(),
                input.memberKeys(), input.mediaKeys(),
                existing.version() + 1, existing.createdAt(), now);
        Event saved = events.update(updated);
        enqueueTreeMutation(tree, "EVENT_UPDATED", saved.externalId(), principal, now);
        return saved;
    }

    @Transactional
    public void delete(Principal principal, String treeExternalId, String externalId,
            Instant now) {
        FamilyTree tree = requireTree(treeExternalId);
        authorization.authorize(tree.treeKey(), principal, Action.WRITE, true);
        Event existing = events.findByExternalId(tree.treeKey(), externalId)
                .orElseThrow(() -> new NotFoundException("EVENT_NOT_FOUND",
                        "Event does not exist"));
        events.delete(tree.treeKey(), existing.eventKey());
        enqueueTreeMutation(tree, "EVENT_DELETED", externalId, principal, now);
    }

    @Transactional(readOnly = true)
    public Optional<Event> detail(Principal principal, String treeExternalId, String externalId) {
        FamilyTree tree = requireTree(treeExternalId);
        authorization.authorize(tree.treeKey(), principal, Action.READ, true);
        return events.findByExternalId(tree.treeKey(), externalId);
    }

    @Transactional(readOnly = true)
    public List<Event> list(Principal principal, String treeExternalId) {
        FamilyTree tree = requireTree(treeExternalId);
        authorization.authorize(tree.treeKey(), principal, Action.READ, true);
        return events.listAll(tree.treeKey()).stream()
                .sorted((a, b) -> {
                    int cmp = a.eventDate().compareTo(b.eventDate());
                    return cmp != 0 ? cmp : a.title().compareTo(b.title());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Event> upcoming(Principal principal, String treeExternalId, int days) {
        if (days < 0 || days > UPCOMING_WINDOW_DAYS) {
            throw new ValidationException("Upcoming window must be 0-" + UPCOMING_WINDOW_DAYS);
        }
        FamilyTree tree = requireTree(treeExternalId);
        authorization.authorize(tree.treeKey(), principal, Action.READ, true);
        LocalDate today = LocalDate.now();
        return events.listAll(tree.treeKey()).stream()
                .filter(event -> withinRecurrence(event, today, days))
                .sorted((left, right) -> {
                    int dateOrder = left.eventDate().compareTo(right.eventDate());
                    return dateOrder != 0 ? dateOrder : left.title().compareTo(right.title());
                })
                .toList();
    }

    /** True when the event recurs within {@code days} from {@code today}, with leap-day mapping. */
    static boolean withinRecurrence(Event event, LocalDate today, int days) {
        LocalDate target = nextOccurrence(event.eventDate(), today);
        if (target == null) {
            return false;
        }
        long span = target.toEpochDay() - today.toEpochDay();
        return span >= 0 && span <= days;
    }

    /**
     * February-29 collapses to February-28 in non-leap years (Req 6.4); in
     * leap years we hit the 29th exactly.
     */
    static LocalDate nextOccurrence(LocalDate eventDate, LocalDate today) {
        if (eventDate == null) {
            return null;
        }
        int year = today.getYear();
        while (true) {
            LocalDate anniversary = eventDate.withYear(year);
            if (anniversary.getMonthValue() == 2 && anniversary.getDayOfMonth() == 29
                    && !anniversary.isLeapYear()) {
                anniversary = anniversary.withDayOfMonth(28);
            }
            if (!anniversary.isBefore(today)) {
                return anniversary;
            }
            year++;
        }
    }

    private FamilyTree requireTree(String externalId) {
        return trees.findByExternalId(externalId)
                .orElseThrow(() -> new NotFoundException("TREE_NOT_FOUND",
                        "Tree does not exist"));
    }

    private static void validateEvent(NewEventInput input) {
        if (input.type() == null) {
            throw new ValidationException("type is required");
        }
        if (input.type() == EventType.CUSTOM
                && (input.customType() == null || input.customType().isBlank())) {
            throw new ValidationException("customType is required for CUSTOM events");
        }
        if (input.title() == null || input.title().isBlank()) {
            throw new ValidationException("title is required");
        }
        if (input.eventDate() == null) {
            throw new ValidationException("eventDate is required");
        }
    }

    private void enqueueTreeMutation(FamilyTree tree, String eventType, String externalId,
            Principal actor, Instant now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("treeExternalId", tree.externalId());
        payload.put("eventExternalId", externalId);
        payload.put("actor", actor == null ? "system" : actor.userId());
        outbox.append("EVENT", externalId, tree.treeKey(), eventType);
    }

    public record NewEventInput(
            EventType type,
            String customType,
            String title,
            LocalDate eventDate,
            String location,
            String description,
            List<Long> memberKeys,
            List<Long> mediaKeys) {}
}
