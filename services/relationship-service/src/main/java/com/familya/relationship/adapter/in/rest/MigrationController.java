package com.familya.relationship.adapter.in.rest;

import com.familya.platform.api.AsyncOperation;
import com.familya.relationship.application.port.in.LoadRelationshipManifestCommand;
import com.familya.relationship.application.usecase.LoadRelationshipManifestUseCase;
import com.familya.relationship.domain.model.Relationship;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/v2/internal/relationship", produces = MediaType.APPLICATION_JSON_VALUE)
public class MigrationController {

    private final LoadRelationshipManifestUseCase loader;

    public MigrationController(LoadRelationshipManifestUseCase loader) {
        this.loader = loader;
    }

    @PostMapping("/migration/relationships")
    public ResponseEntity<AsyncOperation> load(@RequestHeader("X-Correlation-Id") String correlationId,
                                                @Valid @RequestBody LoadManifestRequest req) {
        LoadRelationshipManifestUseCase.LoadResult r = loader.execute(new LoadRelationshipManifestCommand(
                req.treeId(),
                req.relationships().stream().map(l -> new LoadRelationshipManifestCommand.RelationshipLine(
                        l.relationshipId(), Relationship.Kind.valueOf(l.kind()),
                        l.fromMemberId(), l.toMemberId(), l.metadataJson(),
                        l.revision(), l.createdAt() == null ? Instant.now() : l.createdAt())).toList(),
                req.replaySafe()));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(AsyncOperation.accepted(UUID.randomUUID(), "/api/v2/operations/" + UUID.randomUUID()));
    }

    public record LoadManifestRequest(@NotNull UUID treeId, @NotNull List<RelationshipLineDto> relationships, boolean replaySafe) { }

    public record RelationshipLineDto(
            @NotNull UUID relationshipId,
            @NotNull String kind,
            @NotNull UUID fromMemberId,
            @NotNull UUID toMemberId,
            String metadataJson,
            @NotNull Long revision,
            Instant createdAt) { }
}