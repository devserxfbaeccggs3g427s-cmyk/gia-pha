package com.familya.auditops.adapter.in.rest;

import com.familya.auditops.application.port.in.CancelOperationCommand;
import com.familya.auditops.application.port.in.OperatorRetryCommand;
import com.familya.auditops.application.usecase.AuditQueryService;
import com.familya.auditops.application.usecase.OperatorService;
import com.familya.auditops.domain.model.AuditEvent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Operator surface. RBAC is enforced by
 * {@link com.familya.auditops.adapter.in.security.AuditOpsSecurityConfig}
 * via Spring Security's URL-level filter chain. Every action records
 * an audit row with the operator's user id and reason.
 */
@RestController
@RequestMapping(path = "/api/v2/audit-ops", produces = MediaType.APPLICATION_JSON_VALUE)
public class OperatorController {

    private final OperatorService operatorService;
    private final AuditQueryService auditQuery;

    public OperatorController(OperatorService operatorService, AuditQueryService auditQuery) {
        this.operatorService = operatorService;
        this.auditQuery = auditQuery;
    }

    @PostMapping(path = "/operations/{operationId}/retry", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> retry(@PathVariable UUID operationId,
                                      @RequestHeader("X-Acting-User") UUID operatorUserId,
                                      @Valid @RequestBody RetryRequest req,
                                      Authentication auth) {
        operatorService.retry(new OperatorRetryCommand(operatorUserId, operationId, req.reason()),
                roles(auth));
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PostMapping(path = "/operations/{operationId}/cancel", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> cancel(@PathVariable UUID operationId,
                                       @RequestHeader("X-Acting-User") UUID operatorUserId,
                                       @Valid @RequestBody CancelRequest req,
                                       Authentication auth) {
        operatorService.cancel(new CancelOperationCommand(operatorUserId, operationId, req.reason()),
                roles(auth));
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @GetMapping("/operations/{operationId}/audit")
    public ResponseEntity<List<Map<String, Object>>> audit(@PathVariable UUID operationId,
                                                           @RequestParam(defaultValue = "100") int limit) {
        List<AuditEvent> events = auditQuery.byOperation(operationId, limit);
        return ResponseEntity.ok(events.stream().map(OperatorController::toView).toList());
    }

    private static Map<String, Object> toView(AuditEvent e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("auditId", e.auditId());
        m.put("operationId", e.operationId());
        m.put("correlationId", e.correlationId());
        m.put("actorUserId", e.actorUserId());
        m.put("actorKind", e.actorKind().name());
        m.put("action", e.action());
        m.put("targetType", e.targetType());
        m.put("targetId", e.targetId());
        m.put("detail", e.detail());
        m.put("occurredAt", e.occurredAt());
        m.put("traceId", e.traceId());
        return m;
    }

    private static List<String> roles(Authentication auth) {
        if (auth == null || auth.getAuthorities() == null) return List.of();
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
    }

    public record RetryRequest(@NotBlank String reason) { }
    public record CancelRequest(@NotBlank String reason) { }
}