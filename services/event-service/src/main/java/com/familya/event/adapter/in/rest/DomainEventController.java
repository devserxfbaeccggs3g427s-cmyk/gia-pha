package com.familya.event.adapter.in.rest;

import com.familya.event.application.port.in.CreateDomainEventCommand;
import com.familya.event.application.port.in.TombstoneDomainEventCommand;
import com.familya.event.application.port.in.UpdateDomainEventCommand;
import com.familya.event.application.usecase.CreateDomainEventUseCase;
import com.familya.event.application.usecase.QueryDomainEventUseCase;
import com.familya.event.application.usecase.TombstoneDomainEventUseCase;
import com.familya.event.application.usecase.UpdateDomainEventUseCase;
import com.familya.event.domain.model.DomainEvent;
import com.familya.event.domain.model.RecurrenceRule;
import com.familya.platform.api.AsyncOperation;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/v2/trees/{treeId}/events", produces = MediaType.APPLICATION_JSON_VALUE)
public class DomainEventController {

    private final CreateDomainEventUseCase createEvent;
    private final UpdateDomainEventUseCase updateEvent;
    private final TombstoneDomainEventUseCase tombstoneEvent;
    private final QueryDomainEventUseCase query;
    private final ObjectMapper mapper = new ObjectMapper();

    public DomainEventController(CreateDomainEventUseCase createEvent,
                                  UpdateDomainEventUseCase updateEvent,
                                  TombstoneDomainEventUseCase tombstoneEvent,
                                  QueryDomainEventUseCase query) {
        this.createEvent = createEvent;
        this.updateEvent = updateEvent;
        this.tombstoneEvent = tombstoneEvent;
        this.query = query;
    }

    @PostMapping
    public ResponseEntity<AsyncOperation> create(@RequestHeader("X-Acting-User") UUID actingUser,
                                                  @PathVariable UUID treeId,
                                                  @RequestHeader(value = "X-Tree-Revision", required = false) Long expectedTreeRevision,
                                                  @Valid @RequestBody CreateEventRequest req) throws Exception {
        long er = expectedTreeRevision == null ? 0L : expectedTreeRevision;
        UUID id = createEvent.execute(new CreateDomainEventCommand(
                treeId, actingUser, req.title(), req.description(),
                DomainEvent.Kind.valueOf(req.kind()),
                req.startDate(), req.endDate(),
                parseRecurrence(req.recurrence()),
                req.primaryMemberId(),
                req.additionalMemberIds(), req.mediaRefs(), req.location(), er));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .header("Location", "/api/v2/trees/" + treeId + "/events/" + id)
                .body(AsyncOperation.accepted(id, "/api/v2/operations/" + id));
    }

    @PutMapping("/{eventId}")
    public ResponseEntity<AsyncOperation> update(@RequestHeader("X-Acting-User") UUID actingUser,
                                                  @PathVariable UUID treeId,
                                                  @PathVariable UUID eventId,
                                                  @RequestHeader(value = "If-Match", required = false) Long expectedVersion,
                                                  @RequestHeader(value = "X-Tree-Revision", required = false) Long expectedTreeRevision,
                                                  @Valid @RequestBody UpdateEventRequest req) throws Exception {
        long ev = expectedVersion == null ? 0L : expectedVersion;
        long er = expectedTreeRevision == null ? 0L : expectedTreeRevision;
        updateEvent.execute(new UpdateDomainEventCommand(
                eventId, actingUser, ev, er,
                req.title(), req.description(),
                DomainEvent.Kind.valueOf(req.kind()),
                req.startDate(), req.endDate(),
                parseRecurrence(req.recurrence()),
                req.primaryMemberId(),
                req.additionalMemberIds(), req.mediaRefs(), req.location()));
        return ResponseEntity.accepted().body(AsyncOperation.accepted(eventId, "/api/v2/operations/" + eventId));
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<AsyncOperation> tombstone(@RequestHeader("X-Acting-User") UUID actingUser,
                                                     @PathVariable UUID treeId,
                                                     @PathVariable UUID eventId,
                                                     @RequestHeader(value = "If-Match", required = false) Long expectedVersion,
                                                     @RequestHeader(value = "X-Tree-Revision", required = false) Long expectedTreeRevision) {
        long ev = expectedVersion == null ? 0L : expectedVersion;
        long er = expectedTreeRevision == null ? 0L : expectedTreeRevision;
        tombstoneEvent.execute(new TombstoneDomainEventCommand(eventId, actingUser, ev, er));
        return ResponseEntity.accepted().body(AsyncOperation.accepted(eventId, "/api/v2/operations/" + eventId));
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<EventResponse> get(@PathVariable UUID treeId, @PathVariable UUID eventId) {
        DomainEvent ev = query.findById(eventId).orElseThrow(() ->
                new com.familya.platform.error.NotFoundException("Event " + eventId + " not found"));
        return ResponseEntity.ok(EventResponse.from(ev, query.danglingReferences(ev)));
    }

    private RecurrenceRule parseRecurrence(Object payload) throws Exception {
        if (payload == null) return null;
        var m = mapper.convertValue(payload, new TypeReference<java.util.Map<String, Object>>() { });
        String freq = (String) m.get("frequency");
        int interval = ((Number) m.get("interval")).intValue();
        java.util.Map<String, Object> term = (java.util.Map<String, Object>) m.get("termination");
        RecurrenceRule.Termination t;
        if (term.containsKey("count")) t = new RecurrenceRule.Count(((Number) term.get("count")).intValue());
        else t = new RecurrenceRule.Until(LocalDate.parse((String) term.get("until")));
        return new RecurrenceRule(RecurrenceRule.Frequency.valueOf(freq), interval, t);
    }

    public record CreateEventRequest(
            @NotBlank String title,
            String description,
            @NotBlank String kind,
            LocalDate startDate,
            LocalDate endDate,
            Object recurrence,
            UUID primaryMemberId,
            List<UUID> additionalMemberIds,
            List<UUID> mediaRefs,
            String location) { }

    public record UpdateEventRequest(
            String title,
            String description,
            String kind,
            LocalDate startDate,
            LocalDate endDate,
            Object recurrence,
            UUID primaryMemberId,
            List<UUID> additionalMemberIds,
            List<UUID> mediaRefs,
            String location) { }

    public record EventResponse(UUID id, UUID treeId, String title, String description, String kind,
                                 LocalDate startDate, LocalDate endDate, String recurrence,
                                 UUID primaryMemberId, List<UUID> additionalMemberIds, List<UUID> mediaRefs,
                                 String location, java.util.Set<UUID> danglingReferences,
                                 long version, boolean tombstoned) {
        public static EventResponse from(DomainEvent ev, java.util.Set<UUID> dangling) {
            return new EventResponse(ev.id(), ev.treeId(), ev.title(), ev.description(), ev.kind().name(),
                    ev.startDate(), ev.endDate(), null,
                    ev.primaryMemberId(), ev.additionalMemberIds(), ev.mediaRefs(),
                    ev.location(), dangling, ev.version(), ev.isTombstoned());
        }
    }
}