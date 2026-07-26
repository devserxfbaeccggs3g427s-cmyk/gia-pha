package vn.giapha.research.relationships.adapter.in.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.giapha.research.relationships.application.service.RelationshipService;
import vn.giapha.research.relationships.domain.MarriageStatus;
import vn.giapha.research.relationships.domain.RelationType;
import vn.giapha.research.relationships.domain.Relationship;
import vn.giapha.research.relationships.support.RelationshipSupport.ApiSuccess;
import vn.giapha.research.relationships.support.RelationshipSupport.Principal;
import vn.giapha.research.relationships.support.RelationshipSupport.UnauthorizedException;

/**
 * Relationship CRUD controller. Mirrors the legacy BFF surface
 * ({@code /api/trees/{treeId}/relationships}); the BFF forwards
 * requests here via the gateway.
 */
@RestController
@RequestMapping(path = "/api/trees/{treeExternalId}/relationships", produces = "application/json")
public class RelationshipController {

    private final RelationshipService relationships;

    public RelationshipController(RelationshipService relationships) {
        this.relationships = relationships;
    }

    @GetMapping
    ApiSuccess<List<Map<String, Object>>> list(
            @PathVariable String treeExternalId,
            Authentication auth) {
        List<Map<String, Object>> rows = relationships.list(principal(auth), treeExternalId).stream()
                .map(this::toMap).toList();
        return ApiSuccess.ok(rows);
    }

    @PostMapping(consumes = "application/json")
    ApiSuccess<Map<String, Object>> create(
            @PathVariable String treeExternalId,
            @RequestBody RelationshipRequest body,
            Authentication auth) {
        RelationType type = parseType(body.type());
        Relationship created = relationships.create(
                principal(auth),
                treeExternalId,
                body.sourceMemberId(),
                body.targetMemberId(),
                type,
                body.customType(),
                body.marriageDate() == null ? null : LocalDate.parse(body.marriageDate()),
                body.divorceDate() == null ? null : LocalDate.parse(body.divorceDate()),
                body.marriageStatus() == null ? null : MarriageStatus.valueOf(body.marriageStatus()),
                Instant.now());
        return ApiSuccess.ok(toMap(created));
    }

    @DeleteMapping("/{externalId}")
    ApiSuccess<Void> delete(
            @PathVariable String treeExternalId,
            @PathVariable String externalId,
            Authentication auth) {
        relationships.delete(principal(auth), treeExternalId, externalId, Instant.now());
        return ApiSuccess.ok(null);
    }

    private Map<String, Object> toMap(Relationship r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.externalId());
        m.put("treeId", String.valueOf(r.treeKey()));
        m.put("sourceMemberId", r.sourceMemberKey());
        m.put("targetMemberId", r.targetMemberKey());
        m.put("type", r.type().name());
        m.put("createdAt", r.createdAt().toString());
        return m;
    }

    private static RelationType parseType(String s) {
        if (s == null) return RelationType.CUSTOM;
        return RelationType.valueOf(s);
    }

    private static Principal principal(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new UnauthorizedException("UNAUTHORIZED", "Authentication required");
        }
        return new Principal(auth.getName(), auth.getName() + "@giapha.local", "Authenticated user");
    }

    public record RelationshipRequest(
            String sourceMemberId,
            String targetMemberId,
            String type,
            String customType,
            String marriageDate,
            String divorceDate,
            String marriageStatus) {}
}
