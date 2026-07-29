package com.familya.relationship.application.usecase;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.familya.relationship.application.port.in.RestoreMemberRelationshipsCommand;
import com.familya.relationship.application.port.out.RelationshipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Restores relationship edges that were tombstoned by
 * {@link DisableMemberRelationshipsUseCase}. The disable use case persisted a
 * compensation snapshot keyed by {@code operationId} so this restore can
 * deterministically unhide every disabled edge.
 */
@Service
public class RestoreMemberRelationshipsUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(RestoreMemberRelationshipsUseCase.class);

    private final RelationshipRepository repo;
    private final ObjectMapper json;

    public RestoreMemberRelationshipsUseCase(RelationshipRepository repo, ObjectMapper json) {
        this.repo = repo;
        this.json = json;
    }

    @Transactional
    public Result execute(RestoreMemberRelationshipsCommand cmd) {
        String snapshot = repo.loadCompensationSnapshot(cmd.operationId());
        if (snapshot == null) {
            LOG.warn("No compensation snapshot for operationId={} (likely already restored)", cmd.operationId());
            return new Result(0, 0L, 0L);
        }
        try {
            JsonNode root = json.readTree(snapshot);
            long maxVersion = 0L;
            int restored = 0;
            for (JsonNode edge : root.path("edges")) {
                String edgeId = edge.path("id").asText();
                long version = edge.path("version").asLong();
                repo.untombstone(java.util.UUID.fromString(edgeId), Instant.now(), version);
                maxVersion = Math.max(maxVersion, version + 1);
                restored++;
            }
            LOG.info("Restored {} relationship edges for operationId={}",
                    restored, cmd.operationId());
            return new Result(restored, maxVersion, 0L);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to deserialize compensation snapshot for operationId="
                            + cmd.operationId(), e);
        }
    }

    public record Result(int affectedCount, long appliedAggregateVersion, long appliedEpoch) { }
}