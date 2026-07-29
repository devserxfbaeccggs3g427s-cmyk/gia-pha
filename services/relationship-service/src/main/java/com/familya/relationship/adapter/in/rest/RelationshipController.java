package com.familya.relationship.adapter.in.rest;

import com.familya.platform.api.AsyncOperation;
import com.familya.relationship.application.port.in.CreateRelationshipCommand;
import com.familya.relationship.application.port.in.TombstoneRelationshipCommand;
import com.familya.relationship.application.usecase.CreateRelationshipUseCase;
import com.familya.relationship.application.usecase.QueryGraphUseCase;
import com.familya.relationship.application.usecase.TombstoneRelationshipUseCase;
import com.familya.relationship.domain.model.Relationship;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/v2/trees/{treeId}/relationships", produces = MediaType.APPLICATION_JSON_VALUE)
public class RelationshipController {

    private final CreateRelationshipUseCase createRelationship;
    private final TombstoneRelationshipUseCase tombstoneRelationship;
    private final QueryGraphUseCase queryGraph;

    public RelationshipController(CreateRelationshipUseCase createRelationship,
                                  TombstoneRelationshipUseCase tombstoneRelationship,
                                  QueryGraphUseCase queryGraph) {
        this.createRelationship = createRelationship;
        this.tombstoneRelationship = tombstoneRelationship;
        this.queryGraph = queryGraph;
    }

    @PostMapping
    public ResponseEntity<AsyncOperation> create(@RequestHeader("X-Acting-User") UUID actingUser,
                                                  @PathVariable UUID treeId,
                                                  @RequestHeader(value = "X-Tree-Revision", required = false) Long expectedTreeRevision,
                                                  @Valid @RequestBody CreateRelationshipRequest req) {
        long er = expectedTreeRevision == null ? 0L : expectedTreeRevision;
        UUID id = createRelationship.execute(new CreateRelationshipCommand(
                treeId, actingUser, Relationship.Kind.valueOf(req.kind()),
                req.fromMemberId(), req.toMemberId(), req.metadataJson(), er));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .header("Location", "/api/v2/trees/" + treeId + "/relationships/" + id)
                .body(AsyncOperation.accepted(id, "/api/v2/operations/" + id));
    }

    @DeleteMapping("/{relationshipId}")
    public ResponseEntity<AsyncOperation> tombstone(@RequestHeader("X-Acting-User") UUID actingUser,
                                                     @PathVariable UUID treeId,
                                                     @PathVariable UUID relationshipId,
                                                     @RequestHeader(value = "If-Match", required = false) Long expectedVersion,
                                                     @RequestHeader(value = "X-Tree-Revision", required = false) Long expectedTreeRevision) {
        long ev = expectedVersion == null ? 0L : expectedVersion;
        long er = expectedTreeRevision == null ? 0L : expectedTreeRevision;
        tombstoneRelationship.execute(new TombstoneRelationshipCommand(relationshipId, actingUser, ev, er));
        return ResponseEntity.accepted().body(AsyncOperation.accepted(relationshipId,
                "/api/v2/operations/" + relationshipId));
    }

    @GetMapping("/generations")
    public ResponseEntity<Map<UUID, Integer>> generations(@PathVariable UUID treeId,
                                                           @RequestParam("root") UUID root) {
        return ResponseEntity.ok(queryGraph.generations(treeId, root));
    }

    @GetMapping("/ancestors/{memberId}")
    public ResponseEntity<Set<UUID>> ancestors(@PathVariable UUID treeId, @PathVariable UUID memberId) {
        return ResponseEntity.ok(queryGraph.ancestors(treeId, memberId));
    }

    @GetMapping("/spouses/{memberId}")
    public ResponseEntity<Set<UUID>> spouses(@PathVariable UUID treeId, @PathVariable UUID memberId) {
        return ResponseEntity.ok(queryGraph.spouses(treeId, memberId));
    }

    @GetMapping("/adoptions/{memberId}")
    public ResponseEntity<Set<UUID>> adoptions(@PathVariable UUID treeId, @PathVariable UUID memberId) {
        return ResponseEntity.ok(queryGraph.adoptions(treeId, memberId));
    }

    public record CreateRelationshipRequest(@NotNull String kind,
                                              @NotNull UUID fromMemberId,
                                              @NotNull UUID toMemberId,
                                              String metadataJson) { }
}