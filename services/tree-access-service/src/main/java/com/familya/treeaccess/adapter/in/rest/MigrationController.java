package com.familya.treeaccess.adapter.in.rest;

import com.familya.platform.api.AsyncOperation;
import com.familya.treeaccess.application.port.in.LoadTreeManifestCommand;
import com.familya.treeaccess.application.usecase.LoadTreeManifestUseCase;
import com.familya.treeaccess.application.usecase.ReconciliationUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/v2/internal/tree-access", produces = MediaType.APPLICATION_JSON_VALUE)
public class MigrationController {

    private final LoadTreeManifestUseCase loader;
    private final ReconciliationUseCase reconciliation;

    public MigrationController(LoadTreeManifestUseCase loader, ReconciliationUseCase reconciliation) {
        this.loader = loader;
        this.reconciliation = reconciliation;
    }

    @PostMapping("/migration/trees")
    public ResponseEntity<AsyncOperation> load(@RequestHeader("X-Correlation-Id") String correlationId,
                                               @Valid @RequestBody LoadTreeManifestRequest req) {
        LoadTreeManifestUseCase.LoadResult r = loader.execute(new LoadTreeManifestCommand(
                req.treeId(), req.ownerUserId(), req.name(),
                req.initialRevision(), req.initialEpoch(), req.createdAt(),
                req.memberships().stream().map(m -> new LoadTreeManifestCommand.MembershipLine(
                        m.userId(), m.role(), m.grantedBy(), m.grantedAt())).toList(),
                req.replaySafe()));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(AsyncOperation.accepted(r.treeId(), "/api/v2/operations/" + r.treeId()));
    }

    @GetMapping("/reconciliation/{treeId}")
    public ResponseEntity<ReconciliationUseCase.Report> reconcile(@PathVariable UUID treeId) {
        return ResponseEntity.ok(reconciliation.reconcile(treeId));
    }

    public record LoadTreeManifestRequest(
            @NotNull UUID treeId,
            @NotNull UUID ownerUserId,
            @NotBlank String name,
            @NotNull Long initialRevision,
            @NotNull Long initialEpoch,
            @NotNull Instant createdAt,
            @NotNull List<MembershipLineDto> memberships,
            boolean replaySafe) { }

    public record MembershipLineDto(
            @NotNull UUID userId,
            @NotBlank String role,
            @NotNull UUID grantedBy,
            @NotNull Instant grantedAt) { }
}