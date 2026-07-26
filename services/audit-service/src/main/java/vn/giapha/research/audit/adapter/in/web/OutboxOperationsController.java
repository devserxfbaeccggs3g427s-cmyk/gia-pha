package vn.giapha.research.audit.adapter.in.web;

import java.time.Instant;
import java.util.Locale;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import vn.giapha.research.audit.application.port.in.OperateOutboxUseCase;
import vn.giapha.research.audit.domain.model.OutboxEvent;
import vn.giapha.research.audit.domain.model.OutboxStatus;
import vn.giapha.research.audit.support.ApiSuccess;
import vn.giapha.research.audit.support.PageEnvelope;
import vn.giapha.research.audit.support.PageRequest;
import vn.giapha.research.audit.support.PageResult;
import vn.giapha.research.audit.support.ValidationException;

/**
 * Operator API for the transactional outbox (Requirement 13.9): inspect,
 * replay and cancel jobs without direct SQL. Guarded by the {@code OPS}
 * authority — the same bar as the non-health Actuator endpoints — and it
 * answers in the standard legacy envelope so ops tooling shares the client.
 */
@RestController
@RequestMapping(path = "/api/ops/outbox", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAuthority('OPS')")
class OutboxOperationsController {

    private static final int MAX_PAGE_SIZE = 100;

    private final OperateOutboxUseCase operations;
    private final JsonMapper jsonMapper;

    OutboxOperationsController(OperateOutboxUseCase operations, JsonMapper jsonMapper) {
        this.operations = operations;
        this.jsonMapper = jsonMapper;
    }

    @GetMapping
    ApiSuccess<PageEnvelope<OutboxEventResponse>> list(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new ValidationException("page must be >= 1 and pageSize between 1 and "
                    + MAX_PAGE_SIZE);
        }
        PageResult<OutboxEvent> result =
                operations.list(parseStatus(status), new PageRequest(page, pageSize));
        return ApiSuccess.of(new PageEnvelope<>(
                result.items().stream().map(this::toResponse).toList(),
                result.page(), result.size(), result.total(), result.totalPages()));
    }

    @GetMapping("/{id}")
    ApiSuccess<OutboxEventResponse> get(@PathVariable("id") long id) {
        return ApiSuccess.of(toResponse(operations.get(id)));
    }

    @PostMapping("/{id}/replay")
    ApiSuccess<OutboxEventResponse> replay(@PathVariable("id") long id) {
        return ApiSuccess.of(toResponse(operations.replay(id)));
    }

    @PostMapping("/{id}/cancel")
    ApiSuccess<OutboxEventResponse> cancel(@PathVariable("id") long id) {
        return ApiSuccess.of(toResponse(operations.cancel(id)));
    }

    private static OutboxStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return OutboxStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Unknown outbox status: " + status);
        }
    }

    private OutboxEventResponse toResponse(OutboxEvent event) {
        return new OutboxEventResponse(
                event.outboxEventKey(),
                event.aggregateType(),
                event.aggregateExternalId(),
                event.treeKey(),
                event.eventType(),
                jsonMapper.readTree(event.payloadJson()),
                event.status().name(),
                event.attempts(),
                event.availableAt(),
                event.leasedUntil(),
                event.leasedBy(),
                event.lastError(),
                event.completedAt(),
                event.createdAt());
    }

    /** Operator projection of one outbox row; the payload is echoed as parsed JSON. */
    record OutboxEventResponse(
            long id,
            String aggregateType,
            String aggregateId,
            Long treeId,
            String eventType,
            JsonNode payload,
            String status,
            int attempts,
            Instant availableAt,
            Instant leasedUntil,
            String leasedBy,
            String lastError,
            Instant completedAt,
            Instant createdAt) {}
}
