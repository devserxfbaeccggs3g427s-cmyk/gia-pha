package com.familya.treeaccess.adapter.in.rest;

import com.familya.platform.api.AsyncOperation;
import com.familya.treeaccess.application.port.in.AdvanceRevisionCommand;
import com.familya.treeaccess.application.port.in.CreateTreeCommand;
import com.familya.treeaccess.application.port.in.FreezeTreeCommand;
import com.familya.treeaccess.application.port.in.GrantMembershipCommand;
import com.familya.treeaccess.application.port.in.InitiateDeleteTreeCommand;
import com.familya.treeaccess.application.port.in.RevokeMembershipCommand;
import com.familya.treeaccess.application.port.in.TombstoneTreeCommand;
import com.familya.treeaccess.application.port.in.UnfreezeTreeCommand;
import com.familya.treeaccess.application.usecase.AuthorizeUseCase;
import com.familya.treeaccess.application.usecase.CreateTreeUseCase;
import com.familya.treeaccess.application.usecase.DeleteTreeSagaService;
import com.familya.treeaccess.application.usecase.GrantMembershipUseCase;
import com.familya.treeaccess.application.usecase.RevokeMembershipUseCase;
import com.familya.treeaccess.application.usecase.TreeLifecycleUseCases;
import com.familya.treeaccess.domain.model.TreeMembership;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(path = "/api/v2/trees", produces = MediaType.APPLICATION_JSON_VALUE)
public class TreeAccessController {

    private final CreateTreeUseCase createTree;
    private final GrantMembershipUseCase grantMembership;
    private final RevokeMembershipUseCase revokeMembership;
    private final TreeLifecycleUseCases lifecycle;
    private final AuthorizeUseCase authorize;
    private final DeleteTreeSagaService deleteTreeSaga;

    public TreeAccessController(CreateTreeUseCase createTree,
                                GrantMembershipUseCase grantMembership,
                                RevokeMembershipUseCase revokeMembership,
                                TreeLifecycleUseCases lifecycle,
                                AuthorizeUseCase authorize,
                                DeleteTreeSagaService deleteTreeSaga) {
        this.createTree = createTree;
        this.grantMembership = grantMembership;
        this.revokeMembership = revokeMembership;
        this.lifecycle = lifecycle;
        this.authorize = authorize;
        this.deleteTreeSaga = deleteTreeSaga;
    }

    @PostMapping
    public ResponseEntity<AsyncOperation> create(@RequestHeader("X-Acting-User") UUID actingUser,
                                                 @Valid @RequestBody CreateTreeRequest req) {
        UUID treeId = createTree.execute(new CreateTreeCommand(actingUser, req.name()));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .header("Location", "/api/v2/trees/" + treeId)
                .body(AsyncOperation.accepted(treeId, "/api/v2/operations/" + treeId));
    }

    @PostMapping("/{treeId}/memberships")
    public ResponseEntity<AsyncOperation> grant(@RequestHeader("X-Acting-User") UUID actingUser,
                                               @PathVariable UUID treeId,
                                               @Valid @RequestBody GrantRequest req) {
        UUID id = grantMembership.execute(new GrantMembershipCommand(treeId, req.userId(), req.role(), actingUser));
        return ResponseEntity.accepted().body(AsyncOperation.accepted(id, "/api/v2/operations/" + id));
    }

    @DeleteMapping("/{treeId}/memberships/{userId}")
    public ResponseEntity<AsyncOperation> revoke(@RequestHeader("X-Acting-User") UUID actingUser,
                                                 @PathVariable UUID treeId,
                                                 @PathVariable UUID userId,
                                                 @RequestParam(required = false, defaultValue = "manual") String reason) {
        revokeMembership.execute(new RevokeMembershipCommand(treeId, userId, actingUser, reason));
        return ResponseEntity.accepted().body(AsyncOperation.accepted(UUID.randomUUID(),
                "/api/v2/operations/" + UUID.randomUUID()));
    }

    @PostMapping("/{treeId}/freeze")
    public ResponseEntity<AsyncOperation> freeze(@RequestHeader("X-Acting-User") UUID actingUser,
                                                 @PathVariable UUID treeId,
                                                 @RequestHeader(value = "If-Match", required = false) Long expectedVersion) {
        lifecycle.freeze(new FreezeTreeCommand(treeId, actingUser, expectedVersion == null ? 0L : expectedVersion));
        return ResponseEntity.accepted().body(AsyncOperation.accepted(UUID.randomUUID(), "/api/v2/operations/" + UUID.randomUUID()));
    }

    @PostMapping("/{treeId}/unfreeze")
    public ResponseEntity<AsyncOperation> unfreeze(@RequestHeader("X-Acting-User") UUID actingUser,
                                                   @PathVariable UUID treeId,
                                                   @RequestHeader(value = "If-Match", required = false) Long expectedVersion) {
        lifecycle.unfreeze(new UnfreezeTreeCommand(treeId, actingUser, expectedVersion == null ? 0L : expectedVersion));
        return ResponseEntity.accepted().body(AsyncOperation.accepted(UUID.randomUUID(), "/api/v2/operations/" + UUID.randomUUID()));
    }

    @PostMapping("/{treeId}/tombstone")
    public ResponseEntity<AsyncOperation> tombstone(@RequestHeader("X-Acting-User") UUID actingUser,
                                                    @PathVariable UUID treeId,
                                                    @RequestHeader(value = "If-Match", required = false) Long expectedVersion) {
        lifecycle.tombstone(new TombstoneTreeCommand(treeId, actingUser, expectedVersion == null ? 0L : expectedVersion));
        return ResponseEntity.accepted().body(AsyncOperation.accepted(UUID.randomUUID(), "/api/v2/operations/" + UUID.randomUUID()));
    }

    /**
     * Initiates the delete-tree Saga. The HTTP response carries the durable
     * {@code operationId}. Clients poll {@code /api/v2/operations/{id}} for
     * Saga status. Physical binary deletion is deferred to the Media service
     * delayed cleanup worker and is NOT part of this Saga's barrier.
     */
    @DeleteMapping("/{treeId}")
    public ResponseEntity<AsyncOperation> deleteTree(@RequestHeader("X-Acting-User") UUID actingUser,
                                                      @PathVariable UUID treeId,
                                                      @RequestHeader(value = "If-Match", required = false) Long expectedVersion,
                                                      @RequestHeader(value = "X-Tree-Epoch", required = false) Long expectedEpoch,
                                                      @RequestParam(value = "placeRetentionHolds", required = false, defaultValue = "true") boolean placeRetentionHolds) {
        long ev = expectedVersion == null ? 0L : expectedVersion;
        long ee = expectedEpoch == null ? 0L : expectedEpoch;
        UUID operationId = deleteTreeSaga.initiate(new InitiateDeleteTreeCommand(
                treeId, actingUser, ev, ee, placeRetentionHolds));
        return ResponseEntity.accepted()
                .header("Location", "/api/v2/operations/" + operationId)
                .body(AsyncOperation.accepted(operationId, "/api/v2/operations/" + operationId));
    }

    @PostMapping("/{treeId}/revisions")
    public ResponseEntity<AsyncOperation> advanceRevision(@RequestHeader("X-Acting-User") UUID actingUser,
                                                          @PathVariable UUID treeId,
                                                          @Valid @RequestBody AdvanceRevisionRequest req) {
        lifecycle.advanceRevision(new AdvanceRevisionCommand(treeId, actingUser,
                req.expectedVersion(), req.newRevision(), req.newEpoch(), req.reason()));
        return ResponseEntity.accepted().body(AsyncOperation.accepted(UUID.randomUUID(), "/api/v2/operations/" + UUID.randomUUID()));
    }

    @GetMapping("/{treeId}/authorizations/{userId}")
    public ResponseEntity<AuthorizationResponse> authorize(@PathVariable UUID treeId,
                                                           @PathVariable UUID userId,
                                                           @RequestParam(value = "expectedRevision", required = false) Long expectedRevision) {
        long er = expectedRevision == null ? 0L : expectedRevision;
        var projection = authorize.authorize(treeId, userId, er)
                .orElseThrow(() -> new com.familya.platform.error.ForbiddenException(
                        "User " + userId + " is not authorized on tree " + treeId));
        return ResponseEntity.ok(new AuthorizationResponse(
                projection.treeId(), projection.userId(),
                projection.role() == null ? null : projection.role().name(),
                projection.canEdit(), projection.canView(),
                projection.revision(), projection.epoch()));
    }

    public record CreateTreeRequest(@NotBlank String name) { }

    public record GrantRequest(@NotNull UUID userId, @NotNull TreeMembership.Role role) { }

    public record AdvanceRevisionRequest(@NotNull Long expectedVersion, @NotNull Long newRevision,
                                         @NotNull Long newEpoch, @NotBlank String reason) { }

    public record AuthorizationResponse(UUID treeId, UUID userId, String role,
                                        boolean canEdit, boolean canView,
                                        long revision, long epoch) { }
}