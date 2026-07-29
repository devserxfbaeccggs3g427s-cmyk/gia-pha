package com.familya.media.application.usecase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.familya.media.application.port.in.DetachMemberMediaReferencesCommand;
import com.familya.media.application.port.out.MediaReferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Participant step for delete-member Saga. Clears every MEMBER-kind
 * reference whose target is the deleted member. Compensation snapshot is
 * persisted so the references can be restored on Saga failure before the
 * irreversible boundary.
 */
@Service
public class DetachMemberMediaReferencesUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(DetachMemberMediaReferencesUseCase.class);

    private final MediaReferenceRepository refs;
    private final ObjectMapper json;

    public DetachMemberMediaReferencesUseCase(MediaReferenceRepository refs, ObjectMapper json) {
        this.refs = refs;
        this.json = json;
    }

    @Transactional
    public Result execute(DetachMemberMediaReferencesCommand cmd) {
        List<MediaReferenceRepository.ReferenceRow> rows =
                refs.listByTarget(cmd.treeId(), "MEMBER", cmd.memberId());
        refs.saveCompensationSnapshot(cmd.operationId(), serializeSnapshot(rows));
        for (MediaReferenceRepository.ReferenceRow row : rows) {
            refs.clear(row.mediaId(), "MEMBER", cmd.memberId());
        }
        LOG.info("Detached {} media references for member {} on tree {} operationId={}",
                rows.size(), cmd.memberId(), cmd.treeId(), cmd.operationId());
        return new Result(rows.size(),
                Math.max(0L, cmd.targetAggregateVersion()),
                cmd.targetEpoch());
    }

    private String serializeSnapshot(List<MediaReferenceRepository.ReferenceRow> rows) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("rows", rows.stream().map(r -> Map.of(
                "mediaId", r.mediaId().toString(),
                "treeId", r.treeId().toString(),
                "status", r.status(),
                "lastAttemptAt", r.lastAttemptAt() == null ? "" : r.lastAttemptAt().toString()
        )).toList());
        try {
            return json.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize media compensation snapshot", e);
        }
    }

    public record Result(int affectedCount, long appliedAggregateVersion, long appliedEpoch) { }
}