package vn.giapha.research.tree.adapter.in.web;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import vn.giapha.research.tree.infrastructure.kernel.error.UnauthorizedException;
import vn.giapha.research.tree.infrastructure.kernel.principal.Principal;
import vn.giapha.research.tree.infrastructure.kernel.principal.TreeRole;
import vn.giapha.research.tree.infrastructure.kernel.web.ApiSuccess;
import vn.giapha.research.tree.application.service.TreeAuthorizationService;
import vn.giapha.research.tree.application.service.TreeService;
import vn.giapha.research.tree.domain.FamilyTree;
import vn.giapha.research.tree.domain.TreeMembership;

/**
 * Tree and membership controller (Task 22). Mirrors the legacy
 * {@code /api/trees/*} surface so the frontend swap is a no-op during the
 * strangler migration. Every mutation increments the tree revision; the
 * {@code If-Match} header carries the expected version for safe updates.
 */
@RestController
@RequestMapping(path = "/api/trees", produces = "application/json")
public class TreeController {

    private final TreeService treeService;
    private final TreeAuthorizationService authorization;

    public TreeController(TreeService treeService, TreeAuthorizationService authorization) {
        this.treeService = treeService;
        this.authorization = authorization;
    }

    @PostMapping(consumes = "application/json")
    ResponseEntity<ApiSuccess<Map<String, Object>>> create(@Valid @RequestBody CreateRequest body,
            Authentication auth) {
        FamilyTree tree = treeService.create(principal(auth), body.name(), body.description(),
                Instant.now());
        return ResponseEntity.status(201).body(ApiSuccess.ok(toMap(tree)));
    }

    @GetMapping
    ApiSuccess<List<Map<String, Object>>> list(Authentication auth) {
        List<Map<String, Object>> trees = treeService.listForUser(principal(auth)).stream()
                .map(this::toMap).toList();
        return ApiSuccess.ok(trees);
    }

    @GetMapping("/{externalId}")
    ApiSuccess<Map<String, Object>> get(@PathVariable String externalId, Authentication auth) {
        FamilyTree tree = treeService.get(principal(auth), externalId);
        return ApiSuccess.ok(toMap(tree));
    }

    @PatchMapping(path = "/{externalId}", consumes = "application/json")
    ApiSuccess<Map<String, Object>> update(@PathVariable String externalId,
            @RequestHeader("If-Match") long ifMatch,
            @Valid @RequestBody UpdateRequest body, Authentication auth) {
        FamilyTree tree = treeService.update(principal(auth), externalId, body.name(),
                body.description(), ifMatch, Instant.now());
        return ApiSuccess.ok(toMap(tree));
    }

    @DeleteMapping("/{externalId}")
    ResponseEntity<ApiSuccess<Void>> delete(@PathVariable String externalId,
            Authentication auth) {
        treeService.delete(principal(auth), externalId, Instant.now());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{externalId}/tree-memberships")
    ApiSuccess<List<Map<String, Object>>> treeMemberships(@PathVariable String externalId,
            Authentication auth) {
        List<Map<String, Object>> members = treeService.listMemberships(principal(auth),
                externalId).stream().map(this::toMembershipMap).toList();
        return ApiSuccess.ok(members);
    }

    @PostMapping(path = "/{externalId}/tree-memberships", consumes = "application/json")
    ApiSuccess<Map<String, Object>> assignRole(@PathVariable String externalId,
            @Valid @RequestBody RoleAssignment body, Authentication auth) {
        TreeMembership membership = treeService.assignRole(principal(auth), externalId,
                body.userExternalId(), TreeRole.fromWire(body.role()), Instant.now());
        return ApiSuccess.ok(toMembershipMap(membership));
    }

    private static Principal principal(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new UnauthorizedException("UNAUTHORIZED", "Authentication required");
        }
        return new Principal(auth.getName(),
                auth.getName() + "@giapha.local", "Authenticated user");
    }

    private Map<String, Object> toMap(FamilyTree tree) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", tree.externalId());
        map.put("name", tree.name());
        map.put("description", tree.description());
        map.put("ownerUserKey", tree.ownerUserKey());
        map.put("revision", tree.revision());
        map.put("version", tree.version());
        map.put("createdAt", tree.createdAt().toString());
        map.put("updatedAt", tree.updatedAt().toString());
        return map;
    }

    private Map<String, Object> toMembershipMap(TreeMembership membership) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("treeKey", membership.treeKey());
        map.put("userExternalId", membership.userExternalId());
        map.put("role", membership.role().name());
        map.put("version", membership.version());
        return map;
    }

    public record CreateRequest(
            @NotBlank @Size(min = 1, max = 100) String name,
            @Size(max = 1000) String description) {}

    public record UpdateRequest(
            @Size(min = 1, max = 100) String name,
            @Size(max = 1000) String description) {}

    public record RoleAssignment(
            @NotBlank String userExternalId,
            @NotBlank String role) {}
}
