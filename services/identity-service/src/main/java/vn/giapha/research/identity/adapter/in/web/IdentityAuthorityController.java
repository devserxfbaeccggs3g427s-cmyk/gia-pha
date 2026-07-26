package vn.giapha.research.identity.adapter.in.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.giapha.research.identity.application.cutover.IdentityAuthorityService;
import vn.giapha.research.identity.application.cutover.IdentityRollbackProjector;
import vn.giapha.research.identity.domain.cutover.IdentityAuthority;
import vn.giapha.research.identity.domain.cutover.IdentityWriterForbiddenException;
import vn.giapha.research.identity.infrastructure.kernel.web.ApiSuccess;

/**
 * Operator endpoints for the global identity cutover (Task 20). All routes
 * require the {@code OPS} authority; the live {@code identity_authority}
 * row is consulted on every read so a stale cache cannot misroute a write.
 */
@RestController
@RequestMapping(path = "/api/ops/identity-authority",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class IdentityAuthorityController {

    private final IdentityAuthorityService authority;
    private final IdentityRollbackProjector rollback;

    public IdentityAuthorityController(IdentityAuthorityService authority,
            IdentityRollbackProjector rollback) {
        this.authority = authority;
        this.rollback = rollback;
    }

    @GetMapping
    ApiSuccess<Map<String, Object>> current() {
        IdentityAuthority authority = this.authority.current().orElseThrow(() ->
                new IdentityWriterForbiddenException("Identity authority row is missing"));
        return ApiSuccess.ok(asMap(authority));
    }

    @PostMapping("/freeze")
    @PreAuthorize("hasAuthority('OPS')")
    ApiSuccess<Map<String, Object>> freeze(@RequestParam(name = "reason") String reason) {
        return ApiSuccess.ok(asMap(authority.freeze(reason)));
    }

    @PostMapping("/unfreeze")
    @PreAuthorize("hasAuthority('OPS')")
    ApiSuccess<Map<String, Object>> unfreeze(@RequestParam(name = "reason") String reason) {
        return ApiSuccess.ok(asMap(authority.unfreeze(reason)));
    }

    @PostMapping("/switch-to-spring")
    @PreAuthorize("hasAuthority('OPS')")
    ApiSuccess<Map<String, Object>> switchToSpring(@RequestParam(name = "reason") String reason) {
        return ApiSuccess.ok(asMap(authority.switchToSpring(reason)));
    }

    @PostMapping("/rollback-to-legacy")
    @PreAuthorize("hasAuthority('OPS')")
    ApiSuccess<Map<String, Object>> rollbackToLegacy(
            @RequestParam(name = "reason") String reason) {
        return ApiSuccess.ok(asMap(authority.rollbackToLegacy(reason)));
    }

    @GetMapping("/rollback-projection")
    @PreAuthorize("hasAuthority('OPS')")
    ResponseEntity<String> rollbackProjection() {
        return ResponseEntity.ok()
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(rollback.projectJson());
    }

    private static Map<String, Object> asMap(IdentityAuthority authority) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("writer", authority.writer().name());
        map.put("freeze", authority.freeze());
        map.put("legacyReadsAllowed", authority.legacyReadsAllowed());
        map.put("legacyWritesAllowed", authority.legacyWritesAllowed());
        map.put("springReadsAllowed", authority.springReadsAllowed());
        map.put("springWritesAllowed", authority.springWritesAllowed());
        map.put("lastSwitchAt", authority.lastSwitchAt() == null
                ? null : authority.lastSwitchAt().toString());
        map.put("lastSwitchReason", authority.lastSwitchReason());
        map.put("version", authority.version());
        return map;
    }
}
