package vn.giapha.research.events.adapter.in.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import vn.giapha.research.events.application.service.EventService;
import vn.giapha.research.events.domain.Event;
import vn.giapha.research.events.domain.EventType;
import vn.giapha.research.events.support.EventSupport.ApiSuccess;
import vn.giapha.research.events.support.EventSupport.NotFoundException;
import vn.giapha.research.events.support.EventSupport.Principal;
import vn.giapha.research.events.support.EventSupport.UnauthorizedException;

/**
 * Event CRUD controller. Mirrors the legacy BFF surface
 * ({@code /api/trees/{treeId}/events}); the BFF forwards requests
 * via the gateway.
 */
@RestController
@RequestMapping(path = "/api/trees/{treeExternalId}/events", produces = "application/json")
public class EventController {

    private final EventService events;

    public EventController(EventService events) {
        this.events = events;
    }

    @GetMapping
    ApiSuccess<List<Map<String, Object>>> list(
            @PathVariable String treeExternalId,
            Authentication auth) {
        return ApiSuccess.ok(events.list(principal(auth), treeExternalId)
                .stream().map(this::toMap).toList());
    }

    @GetMapping("/upcoming")
    ApiSuccess<List<Map<String, Object>>> upcoming(
            @PathVariable String treeExternalId,
            Authentication auth,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "7") int days) {
        return ApiSuccess.ok(events.upcoming(principal(auth), treeExternalId, days)
                .stream().map(this::toMap).toList());
    }

    @PostMapping(consumes = "application/json")
    ApiSuccess<Map<String, Object>> create(
            @PathVariable String treeExternalId,
            @Valid @RequestBody EventRequest body,
            Authentication auth) {
        EventService.NewEventInput input = new EventService.NewEventInput(
                body.eventType() == null ? null : EventType.valueOf(body.eventType()),
                body.customType(),
                body.title(),
                body.eventDate() == null ? null : LocalDate.parse(body.eventDate()),
                body.location(),
                body.description(),
                idsToKeys(body.memberIds()),
                idsToKeys(body.mediaIds()));
        Event created = events.create(principal(auth), treeExternalId, input, Instant.now());
        return ApiSuccess.ok(toMap(created));
    }

    @GetMapping("/{externalId}")
    ApiSuccess<Map<String, Object>> detail(
            @PathVariable String treeExternalId,
            @PathVariable String externalId,
            Authentication auth) {
        return events.detail(principal(auth), treeExternalId, externalId)
                .map(this::toMap)
                .map(ApiSuccess::ok)
                .orElseThrow(() -> new NotFoundException("NOT_FOUND", "Event not found"));
    }

    @DeleteMapping("/{externalId}")
    ApiSuccess<Void> delete(
            @PathVariable String treeExternalId,
            @PathVariable String externalId,
            Authentication auth) {
        events.delete(principal(auth), treeExternalId, externalId, Instant.now());
        return ApiSuccess.ok(null);
    }

    private Map<String, Object> toMap(Event e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.externalId());
        m.put("treeId", String.valueOf(e.treeKey()));
        m.put("title", e.title());
        m.put("type", e.type() == null ? null : e.type().name());
        m.put("customType", e.customType());
        m.put("eventDate", e.eventDate() == null ? null : e.eventDate().toString());
        m.put("location", e.location());
        m.put("description", e.description());
        m.put("createdAt", e.createdAt() == null ? null : e.createdAt().toString());
        m.put("updatedAt", e.updatedAt() == null ? null : e.updatedAt().toString());
        return m;
    }

    /**
     * The BFF forwards external ids (nanoid strings). The microservice
     * joins against BIGINT keys. For research-lane smoke we hash the
     * external id into a deterministic long; production routes would
     * resolve via {@code members-service}/{@code media-metadata-service}.
     */
    private static List<Long> idsToKeys(List<String> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<Long> keys = new ArrayList<>(ids.size());
        for (String id : ids) {
            keys.add((long) id.hashCode());
        }
        return keys;
    }

    private static Principal principal(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new UnauthorizedException("UNAUTHORIZED", "Authentication required");
        }
        return new Principal(auth.getName(), auth.getName() + "@giapha.local", "Authenticated user");
    }

    public record EventRequest(
            String title,
            String eventType,
            String eventDate,
            String customType,
            String location,
            String description,
            List<String> memberIds,
            List<String> mediaIds) {}
}
