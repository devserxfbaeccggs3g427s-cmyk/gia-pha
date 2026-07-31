package com.familya.sharing.adapter.in.rest;

import com.familya.sharing.application.port.in.CreateShareLinkCommand;
import com.familya.sharing.application.port.out.ShareLinkRepository;
import com.familya.sharing.application.usecase.CreateShareLinkUseCase;
import com.familya.sharing.application.usecase.RebuildPublicProjectionUseCase;
import com.familya.sharing.application.port.out.AllowlistedProjectionRepository.ShareScope;
import com.familya.platform.api.AsyncOperation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/v2/internal/sharing", produces = MediaType.APPLICATION_JSON_VALUE)
public class SharingMigrationController {

    private final RebuildPublicProjectionUseCase rebuild;
    private final CreateShareLinkUseCase createShareLink;
    private final ShareLinkRepository repo;

    public SharingMigrationController(RebuildPublicProjectionUseCase rebuild,
                                       CreateShareLinkUseCase createShareLink,
                                       ShareLinkRepository repo) {
        this.rebuild = rebuild;
        this.createShareLink = createShareLink;
        this.repo = repo;
    }

    @PostMapping(path = "/migration/projection/rebuild", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AsyncOperation> rebuild(@RequestHeader("X-Correlation-Id") String correlationId,
                                                    @Valid @RequestBody RebuildRequest req) {
        rebuild.execute(req.treeId(), ShareScope.valueOf(req.scope()), req.targetId(), req.watermark());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(AsyncOperation.accepted(req.treeId(), "/api/v2/operations/" + req.treeId()));
    }

    @PostMapping(path = "/migration/legacy-token", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AsyncOperation> importLegacy(@RequestHeader("X-Correlation-Id") String correlationId,
                                                         @Valid @RequestBody LegacyTokenRequest req) {
        String hash = CreateShareLinkUseCase.sha256Hex(req.token());
        if (repo.findByTokenHash(hash).isEmpty()) {
            createShareLink.execute(new CreateShareLinkCommand(
                    req.treeId(), req.actingUser(), 0L, req.scope(), req.targetId(), req.role(), req.expiresAt()));
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(AsyncOperation.accepted(req.treeId(), "/api/v2/operations/" + req.treeId()));
    }

    public record RebuildRequest(
            @NotNull UUID treeId,
            @NotNull String scope,
            UUID targetId,
            long watermark) { }

    public record LegacyTokenRequest(
            @NotNull UUID treeId,
            @NotNull UUID actingUser,
            @NotNull String token,
            @NotNull String scope,
            UUID targetId,
            String role,
            Instant expiresAt) { }
}
