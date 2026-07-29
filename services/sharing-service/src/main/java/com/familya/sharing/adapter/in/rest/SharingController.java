package com.familya.sharing.adapter.in.rest;

import com.familya.sharing.application.port.in.CreateShareLinkCommand;
import com.familya.sharing.application.port.in.RevokeShareLinkCommand;
import com.familya.sharing.application.port.out.ShareLinkRepository;
import com.familya.sharing.application.usecase.CreateShareLinkUseCase;
import com.familya.sharing.application.usecase.RevokeShareLinkUseCase;
import com.familya.sharing.domain.model.ShareLink;
import com.familya.platform.api.AsyncOperation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/v2/sharing", produces = MediaType.APPLICATION_JSON_VALUE)
public class SharingController {

    private final CreateShareLinkUseCase createShareLink;
    private final RevokeShareLinkUseCase revokeShareLink;
    private final ShareLinkRepository repo;

    public SharingController(CreateShareLinkUseCase createShareLink,
                              RevokeShareLinkUseCase revokeShareLink,
                              ShareLinkRepository repo) {
        this.createShareLink = createShareLink;
        this.revokeShareLink = revokeShareLink;
        this.repo = repo;
    }

    @PostMapping(path = "/links", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ShareLinkResponse> create(@RequestHeader("X-Acting-User") UUID actingUser,
                                                     @RequestHeader(value = "X-Tree-Revision", required = false) Long expectedTreeRevision,
                                                     @Valid @RequestBody CreateLinkRequest req) {
        long er = expectedTreeRevision == null ? 0L : expectedTreeRevision;
        Instant expiresAt = req.expiresAtEpochMs() == null ? null : Instant.ofEpochMilli(req.expiresAtEpochMs());
        CreateShareLinkUseCase.Result r = createShareLink.execute(new CreateShareLinkCommand(
                req.treeId(), actingUser, er, req.scope(), req.targetId(), req.role(), expiresAt));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ShareLinkResponse(r.shareId(), r.token(), r.expiresAt()));
    }

    @GetMapping("/links")
    public ResponseEntity<List<ShareLinkView>> list(@RequestHeader("X-Acting-User") UUID actingUser,
                                                     @RequestParam("treeId") UUID treeId) {
        var rows = repo.listByTree(treeId).stream()
                .map(l -> new ShareLinkView(l.id(), l.scope().name(), l.targetId(), l.role().name(),
                        l.createdAt(), l.expiresAt(), l.revokedAt(), l.revocationReason()))
                .toList();
        return ResponseEntity.ok(rows);
    }

    @DeleteMapping("/links/{shareId}")
    public ResponseEntity<AsyncOperation> revoke(@RequestHeader("X-Acting-User") UUID actingUser,
                                                  @PathVariable UUID shareId,
                                                  @RequestHeader(value = "If-Match", required = false) Long expectedVersion,
                                                  @RequestHeader(value = "X-Tree-Revision", required = false) Long expectedTreeRevision,
                                                  @RequestParam(value = "reason", required = false) String reason) {
        long ev = expectedVersion == null ? 0L : expectedVersion;
        long er = expectedTreeRevision == null ? 0L : expectedTreeRevision;
        revokeShareLink.execute(new RevokeShareLinkCommand(shareId, actingUser, ev, er, reason));
        return ResponseEntity.accepted().body(AsyncOperation.accepted(shareId, "/api/v2/operations/" + shareId));
    }

    public record CreateLinkRequest(
            @jakarta.validation.constraints.NotNull UUID treeId,
            @NotBlank String scope,
            UUID targetId,
            String role,
            Long expiresAtEpochMs) { }

    public record ShareLinkResponse(UUID shareId, String token, Instant expiresAt) { }

    public record ShareLinkView(UUID shareId, String scope, UUID targetId, String role,
                                 Instant createdAt, Instant expiresAt, Instant revokedAt, String reason) { }
}
