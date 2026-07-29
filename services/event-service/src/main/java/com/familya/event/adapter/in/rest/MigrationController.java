package com.familya.event.adapter.in.rest;

import com.familya.event.application.port.in.LoadEventCommand;
import com.familya.event.application.usecase.LoadEventUseCase;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/v2/internal/event", produces = MediaType.APPLICATION_JSON_VALUE)
public class MigrationController {

    private final LoadEventUseCase loader;
    private final ObjectMapper mapper = new ObjectMapper();

    public MigrationController(LoadEventUseCase loader) {
        this.loader = loader;
    }

    @PostMapping("/migration/events")
    public ResponseEntity<AsyncOperation> load(@RequestHeader("X-Correlation-Id") String correlationId,
                                                @Valid @RequestBody LoadEventRequest req) throws Exception {
        LoadEventUseCase.LoadResult r = loader.execute(new LoadEventCommand(
                req.eventId(), req.treeId(), req.title(), req.description(),
                DomainEvent.Kind.valueOf(req.kind()),
                req.startDate(), req.endDate(),
                parseRecurrence(req.recurrence()),
                req.primaryMemberId(),
                req.additionalMemberIds(), req.mediaRefs(), req.location(),
                req.createdAt() == null ? Instant.now() : req.createdAt(),
                req.updatedAt() == null ? Instant.now() : req.updatedAt(),
                Boolean.TRUE.equals(req.tombstoned()),
                req.replaySafe()));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(AsyncOperation.accepted(r.eventId(), "/api/v2/operations/" + r.eventId()));
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

    public record LoadEventRequest(
            @NotNull UUID eventId,
            @NotNull UUID treeId,
            @NotBlank String title,
            String description,
            @NotBlank String kind,
            LocalDate startDate,
            LocalDate endDate,
            Object recurrence,
            UUID primaryMemberId,
            List<UUID> additionalMemberIds,
            List<UUID> mediaRefs,
            String location,
            Instant createdAt,
            Instant updatedAt,
            Boolean tombstoned,
            boolean replaySafe) { }
}