package vn.giapha.research.sharing.adapter.in.web;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import vn.giapha.research.sharing.application.service.ShareService;
import vn.giapha.research.sharing.security.UserContextResolver;

@RestController
public class ShareController {

    private final ShareService share;
    private final UserContextResolver userContextResolver;

    public ShareController(ShareService share, UserContextResolver userContextResolver) {
        this.share = share;
        this.userContextResolver = userContextResolver;
    }

    @PostMapping(path = "/api/trees/{treeExternalId}/shares", produces = "application/json")
    ResponseEntity<Map<String, Object>> create(@PathVariable String treeExternalId,
            @Valid @RequestBody CreateShare body,
            @RequestHeader(name = "X-User-Context-Token", required = false) String userContext) {
        ShareService.IssuedShareLink issued = share.create(userContextResolver.resolve(userContext),
                treeExternalId, body.ttlDays() == null ? null : Duration.ofDays(body.ttlDays()),
                body.scopes() == null ? Set.of() : body.scopes(), Instant.now());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", issued.externalId());
        response.put("token", issued.rawToken());
        response.put("expiresAt", issued.expiresAt().toString());
        response.put("url", "/api/public/share/" + issued.rawToken());
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
    }

    @GetMapping(path = "/api/trees/{treeExternalId}/shares", produces = "application/json")
    ResponseEntity<List<Map<String, Object>>> list(@PathVariable String treeExternalId,
            @RequestHeader(name = "X-User-Context-Token", required = false) String userContext) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(share.list(userContextResolver.resolve(userContext), treeExternalId));
    }

    @DeleteMapping(path = "/api/trees/{treeExternalId}/shares/{shareExternalId}")
    ResponseEntity<Void> revoke(@PathVariable String treeExternalId,
            @PathVariable String shareExternalId,
            @RequestHeader(name = "X-User-Context-Token", required = false) String userContext) {
        share.revoke(userContextResolver.resolve(userContext), shareExternalId, Instant.now());
        return ResponseEntity.noContent().build();
    }

    @GetMapping(path = "/api/public/share/{token}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> resolvePublic(@PathVariable String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Robots-Tag", "noindex")
                .body(share.resolvePublicView(token, Instant.now()).toMap());
    }

    @GetMapping(path = "/api/public/share/{token}/media/{mediaExternalId}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> sharedMedia(@PathVariable String token,
            @PathVariable String mediaExternalId) {
        return share.resolveSharedMedia(token, mediaExternalId, Instant.now())
                .map(access -> ResponseEntity.ok().cacheControl(CacheControl.noStore())
                        .header("X-Robots-Tag", "noindex")
                        .body(Map.<String, Object>of(
                                "treeKey", access.treeKey(),
                                "mediaExternalId", access.mediaExternalId(),
                                "expiresAt", access.expiresAt().toString())))
                .orElseGet(() -> ResponseEntity.status(403).header("X-Robots-Tag", "noindex")
                        .body(Map.of("error", "FORBIDDEN")));
    }

    public record CreateShare(@Min(1) @Max(365) Long ttlDays, Set<String> scopes) {
    }
}
