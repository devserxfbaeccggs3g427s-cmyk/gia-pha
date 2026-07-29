package com.familya.relationship.application.usecase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.familya.relationship.application.port.in.DisableMemberRelationshipsCommand;
import com.familya.relationship.application.port.out.RelationshipRepository;
import com.familya.relationship.domain.model.Relationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Participant step for delete-member Saga. Tombstones every active edge that
 * touches the member, persists a compensation snapshot, and returns the
 * applied aggregate version and epoch for the orchestrator's barrier check.
 */
@Service
public class DisableMemberRelationshipsUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(DisableMemberRelationshipsUseCase.class);

    private final RelationshipRepository repo;
    private final ObjectMapper json;

    public DisableMemberRelationshipsUseCase(RelationshipRepository repo, ObjectMapper json) {
        this.repo = repo;
        this.json = json;
    }

    @Transactional
    public Result execute(DisableMemberRelationshipsCommand cmd) {
        List<Relationship> active = repo.listActiveByMember(cmd.treeId(), cmd.memberId());
        repo.saveCompensationSnapshot(cmd.operationId(), serialize(active));

        Instant now = Instant.now();
        long maxVersion = 0L;
        for (Relationship r : active) {
            r.tombstone(r.version(), now);
            repo.update(r);
            maxVersion = Math.max(maxVersion, r.version());
        }

        long appliedVersion = Math.max(maxVersion, cmd.targetAggregateVersion());
        long appliedEpoch = Math.max(cmd.expectedEpoch(), cmd.targetEpoch());

        LOG.info("Disabled {} edges for member {} on tree {} operationId={}",
                active.size(), cmd.memberId(), cmd.treeId(), cmd.operationId());
        return new Result(active.size(), appliedVersion, appliedEpoch);
    }

    private String serialize(List<Relationship> active) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("memberId", active.isEmpty() ? null : active.get(0).treeId().toString());
            root.put("edges", active.stream().map(r -> Map.of(
                    "id", r.id().toString(),
                    "from", r.fromMemberId().toString(),
                    "to", r.toMemberId().toString(),
                    "kind", r.kind().name(),
                    "version", r.version())).toList());
            return json.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize compensation snapshot", e);
        }
    }

    public record Result(int affectedCount, long appliedAggregateVersion, long appliedEpoch) { }
}