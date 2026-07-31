/**
 * REST controller cho bề mặt vận hành (operator surface) của audit-ops.
 *
 * <p>Audit Ops chỉ là projection. Theo Task 13.1 và ADR-003, operator
 * action không được thực hiện trực tiếp bởi Audit Ops: controller này
 * chỉ ghi nhận <em>intent</em> vào {@code audit_evidence} (append-only,
 * allowlist) và trả về 202. Owning service sẽ áp dụng intent thông
 * qua đường retry/cancel riêng của mình (Saga state machine, optimistic
 * version, authorization). Khi intent được ghi nhận, owning service
 * cập nhật Saga state và publish {@code OperationStateChanged} qua
 * outbox; Audit Ops projection sẽ thấy kết quả khi consumer đọc
 * {@code operations.events.v1}.</p>
 *
 * <p>Phân quyền được thực thi ở {@link com.familya.auditops.adapter.in.security.AuditOpsSecurityConfig}
 * thông qua Spring Security filter chain ở cấp URL.</p>
 */
package com.familya.auditops.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.familya.platform.telemetry.PlatformMetrics;
import com.familya.auditops.adapter.out.persistence.JdbcOperationLifecycleProjection;
import com.familya.auditops.adapter.out.persistence.OperatorIntentStore;
import com.familya.auditops.domain.model.OperationLifecycleRow;
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

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Cung cấp các endpoint read-only và operator intent:
 * <ul>
 *   <li>{@code POST /api/v2/audit-ops/operations/{id}/retry}</li>
 *   <li>{@code POST /api/v2/audit-ops/operations/{id}/cancel}</li>
 *   <li>{@code POST /api/v2/audit-ops/operations/{id}/resolve}</li>
 *   <li>{@code GET  /api/v2/audit-ops/operations/{id}/audit}</li>
 *   <li>{@code GET  /api/v2/audit-ops/overdue}</li>
 *   <li>{@code GET  /api/v2/audit-ops/manual-review}</li>
 *   <li>{@code GET  /api/v2/audit-ops/dlq}</li>
 *   <li>{@code GET  /api/v2/audit-ops/projection/watermarks}</li>
 *   <li>{@code GET  /api/v2/audit-ops/operator-actions}</li>
 * </ul>
 */
@RestController
@RequestMapping(path = "/api/v2/audit-ops", produces = MediaType.APPLICATION_JSON_VALUE)
public class OperatorController {

    private final OperatorIntentStore intents;
    private final JdbcOperationLifecycleProjection projection;
    private final PlatformMetrics metrics;
    private final ObjectMapper json;

    public OperatorController(OperatorIntentStore intents,
                              JdbcOperationLifecycleProjection projection,
                              PlatformMetrics metrics,
                              ObjectMapper json) {
        this.intents = intents;
        this.projection = projection;
        this.metrics = metrics;
        this.json = json;
    }

    @PostMapping(path = "/operations/{operationId}/retry", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> retry(@PathVariable UUID operationId,
                                                     @RequestHeader("X-Acting-User") UUID operatorUserId,
                                                     @Valid @RequestBody RetryRequest req,
                                                     Authentication auth) {
        Map<String, Object> payload = Map.of("reason", req.reason());
        UUID intentId = intents.recordIntent("operation.retry", operationId, operatorUserId,
                "OPERATOR", payload, roles(auth));
        metrics.mutationAccepted("audit-ops-service", "operator_retry_intent");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("intentId", intentId.toString());
        body.put("operationId", operationId.toString());
        body.put("action", "operation.retry");
        body.put("status", "PENDING_OWNER_DISPATCH");
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    @PostMapping(path = "/operations/{operationId}/cancel", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable UUID operationId,
                                                      @RequestHeader("X-Acting-User") UUID operatorUserId,
                                                      @Valid @RequestBody CancelRequest req,
                                                      Authentication auth) {
        Map<String, Object> payload = Map.of("reason", req.reason());
        UUID intentId = intents.recordIntent("operation.cancel", operationId, operatorUserId,
                "OPERATOR", payload, roles(auth));
        metrics.mutationAccepted("audit-ops-service", "operator_cancel_intent");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("intentId", intentId.toString());
        body.put("operationId", operationId.toString());
        body.put("action", "operation.cancel");
        body.put("status", "PENDING_OWNER_DISPATCH");
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    @PostMapping(path = "/operations/{operationId}/resolve", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> resolve(@PathVariable UUID operationId,
                                                       @RequestHeader("X-Acting-User") UUID operatorUserId,
                                                       @Valid @RequestBody ResolveRequest req,
                                                       Authentication auth) {
        Map<String, Object> payload = Map.of("resolution", req.resolution(), "note", req.note() == null ? "" : req.note());
        UUID intentId = intents.recordIntent("operation.resolve", operationId, operatorUserId,
                "OPERATOR", payload, roles(auth));
        metrics.mutationAccepted("audit-ops-service", "operator_resolve_intent");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("intentId", intentId.toString());
        body.put("operationId", operationId.toString());
        body.put("action", "operation.resolve");
        body.put("status", "PENDING_OWNER_DISPATCH");
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    @GetMapping("/operations/{operationId}/audit")
    public ResponseEntity<List<Map<String, Object>>> audit(@PathVariable UUID operationId,
                                                           @RequestParam(defaultValue = "100") int limit) {
        List<Map<String, Object>> events = intents.findByOperation(operationId, clamp(limit));
        return ResponseEntity.ok(events);
    }

    @GetMapping("/overdue")
    public ResponseEntity<List<Map<String, Object>>> overdue(@RequestParam(defaultValue = "60") int minutes,
                                                             @RequestParam(defaultValue = "100") int limit) {
        Instant olderThan = Instant.now().minusSeconds(60L * minutes);
        List<OperationLifecycleRow> rows = projection.findStale(olderThan, clamp(limit));
        return ResponseEntity.ok(rows.stream().map(OperatorController::toView).toList());
    }

    @GetMapping("/manual-review")
    public ResponseEntity<List<Map<String, Object>>> manualReview(@RequestParam(defaultValue = "100") int limit) {
        List<OperationLifecycleRow> rows = projection.findByState("MANUAL_REVIEW", clamp(limit));
        return ResponseEntity.ok(rows.stream().map(OperatorController::toView).toList());
    }

    @GetMapping("/dlq")
    public ResponseEntity<List<Map<String, Object>>> dlq(@RequestParam(defaultValue = "100") int limit) {
        List<Map<String, Object>> lifecycle = intents.findLifecycleDeadLetter(clamp(limit));
        List<Map<String, Object>> reply = intents.findSagaReplyDeadLetter(clamp(limit));
        lifecycle.addAll(reply);
        return ResponseEntity.ok(lifecycle);
    }

    @GetMapping("/projection/watermarks")
    public ResponseEntity<List<Map<String, Object>>> watermarks() {
        List<com.familya.auditops.application.port.out.OperationLifecycleProjection.Watermark> marks = projection.listWatermarks();
        return ResponseEntity.ok(marks.stream().map(OperatorController::toView).toList());
    }

    @GetMapping("/operator-actions")
    public ResponseEntity<List<Map<String, Object>>> operatorActions(@RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(intents.findOperatorActions(clamp(limit)));
    }

    private static int clamp(int limit) {
        return Math.min(Math.max(limit, 1), 1000);
    }

    private static Map<String, Object> toView(OperationLifecycleRow r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("operationId", r.operationId().toString());
        m.put("ownerService", r.ownerService());
        m.put("sagaType", r.sagaType());
        m.put("treeId", r.treeId() == null ? null : r.treeId().toString());
        m.put("state", r.state());
        m.put("targetVersion", r.targetVersion());
        m.put("targetEpoch", r.targetEpoch());
        m.put("failureCode", r.failureCode());
        m.put("failureMessage", r.failureMessage());
        m.put("failureRouting", r.failureRouting());
        m.put("startedAt", r.startedAt());
        m.put("updatedAt", r.updatedAt());
        m.put("finalizedAt", r.finalizedAt());
        return m;
    }

    private static Map<String, Object> toView(com.familya.auditops.application.port.out.OperationLifecycleProjection.Watermark w) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("topic", w.topic());
        m.put("lastOffset", w.lastOffset());
        m.put("lastSeenAt", w.lastSeenAt());
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
    public record ResolveRequest(@NotBlank String resolution, String note) { }
}
