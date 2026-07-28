package com.familya.auditops.adapter.in.rest;

import com.familya.auditops.application.port.in.StartOperationCommand;
import com.familya.auditops.application.usecase.OperationService;
import com.familya.platform.api.AsyncOperation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Public registration endpoint for cross-service operations. Returns
 * the {@code 202 Accepted} envelope required by ADR-007. The
 * platform {@code OperationController} handles polling at
 * {@code GET /api/v2/operations/{id}}.
 */
@RestController
@RequestMapping(path = "/api/v2/audit-ops/operations", produces = MediaType.APPLICATION_JSON_VALUE)
public class OperationRegistrationController {

    private final OperationService service;

    public OperationRegistrationController(OperationService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AsyncOperation> register(
            @RequestHeader(name = "X-Acting-User", required = false) UUID actingUser,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody RegisterRequest req) {

        StartOperationCommand cmd = new StartOperationCommand(
                actingUser,
                req.operationType(),
                req.aggregateType(),
                req.aggregateId(),
                req.treeId(),
                req.targetRevision(),
                req.targetEpoch(),
                req.detail() == null ? Map.of() : req.detail(),
                idempotencyKey,
                sha256Hex(req),
                correlationId,
                req.startedAt() == null ? Instant.now() : req.startedAt());

        AsyncOperation envelope = service.register(cmd);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .header("Location", "/api/v2/operations/" + envelope.operationId())
                .body(envelope);
    }

    public record RegisterRequest(
            @NotBlank String operationType,
            String aggregateType,
            String aggregateId,
            UUID treeId,
            Long targetRevision,
            Long targetEpoch,
            Map<String, Object> detail,
            Instant startedAt) { }

    private static String sha256Hex(RegisterRequest r) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String body = (r.operationType() == null ? "" : r.operationType()) + "|"
                    + (r.aggregateId() == null ? "" : r.aggregateId()) + "|"
                    + (r.targetRevision() == null ? "" : r.targetRevision()) + "|"
                    + (r.targetEpoch() == null ? "" : r.targetEpoch());
            byte[] hash = md.digest(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}