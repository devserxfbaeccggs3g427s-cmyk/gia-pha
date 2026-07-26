package vn.giapha.research.sharing.application.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import vn.giapha.research.sharing.application.port.out.PublicProjectionRepository;
import vn.giapha.research.sharing.application.port.out.ShareLinkRepository;
import vn.giapha.research.sharing.application.port.out.TreeAccessPort;
import vn.giapha.research.sharing.domain.model.PublicTreeView;
import vn.giapha.research.sharing.domain.model.ShareLink;
import vn.giapha.research.sharing.domain.model.ShareLink.SharePermission;
import vn.giapha.research.sharing.security.Principal;
import vn.giapha.research.sharing.util.Hashes;
import vn.giapha.research.sharing.util.Ids;

@Service
public class ShareService {

    private static final Duration DEFAULT_TTL = Duration.ofDays(30);
    private static final Duration MAX_TTL = Duration.ofDays(365);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ShareLinkRepository repository;
    private final TreeAccessPort trees;
    private final PublicProjectionRepository projection;

    public ShareService(ShareLinkRepository repository, TreeAccessPort trees,
            PublicProjectionRepository projection) {
        this.repository = repository;
        this.trees = trees;
        this.projection = projection;
    }

    @Transactional
    public IssuedShareLink create(Principal principal, String treeExternalId,
            Duration ttl, Set<String> scopes, Instant now) {
        TreeAccessPort.Tree tree = trees.authorize(treeExternalId, principal);
        Duration effectiveTtl = ttl == null || ttl.isNegative() || ttl.isZero()
                ? DEFAULT_TTL : ttl;
        if (effectiveTtl.compareTo(MAX_TTL) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Share lifetime cannot exceed 365 days");
        }
        byte[] rawToken = new byte[32];
        RANDOM.nextBytes(rawToken);
        byte[] nonce = new byte[16];
        RANDOM.nextBytes(nonce);
        String externalId = Ids.newId();
        Long userKey = numericUserKey(principal.userId());
        ShareLink link = new ShareLink(0, externalId, tree.treeKey(),
                Hashes.sha256(rawToken), nonce, SharePermission.VIEW,
                now.plus(effectiveTtl), null, userKey,
                scopes == null ? Set.of() : scopes, 1L, now, now);
        repository.insert(link);
        return new IssuedShareLink(externalId, base64Url(rawToken), link.expiresAt());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Principal principal, String treeExternalId) {
        TreeAccessPort.Tree tree = trees.authorize(treeExternalId, principal);
        return repository.listActiveByTree(tree.treeKey(), Instant.now()).stream()
                .map(link -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", link.externalId());
                    map.put("permission", link.permission().name());
                    map.put("expiresAt", link.expiresAt().toString());
                    map.put("scopes", link.allowedScopes());
                    return map;
                }).toList();
    }

    @Transactional
    public boolean revoke(Principal principal, String externalId, Instant now) {
        ShareLink link = repository.findByExternalId(externalId)
                .orElseThrow(() -> notFound("Share link does not exist"));
        TreeAccessPort.Tree tree = trees.findByKey(link.treeKey())
                .orElseThrow(() -> notFound("Tree does not exist"));
        trees.authorize(tree.externalId(), principal);
        return repository.revoke(link.shareLinkKey(), now);
    }

    @Transactional(readOnly = true)
    public PublicTreeView resolvePublicView(String rawToken, Instant now) {
        ShareLink link = resolveLink(rawToken, now);
        TreeAccessPort.Tree tree = trees.findByKey(link.treeKey())
                .orElseThrow(() -> notFound("Tree no longer exists"));
        return new PublicTreeView(tree.name(), tree.description(), now.toString(),
                projection.members(tree.treeKey()),
                projection.relationships(tree.treeKey()),
                projection.events(tree.treeKey()));
    }

    @Transactional(readOnly = true)
    public Optional<SignedMediaAccess> resolveSharedMedia(String rawToken,
            String requestedMediaExternalId, Instant now) {
        ShareLink link = resolveLink(rawToken, now);
        if (!projection.containsMedia(link.treeKey(), requestedMediaExternalId)) {
            return Optional.empty();
        }
        return Optional.of(new SignedMediaAccess(link.treeKey(), requestedMediaExternalId,
                now.plus(Duration.ofMinutes(5))));
    }

    private ShareLink resolveLink(String rawToken, Instant now) {
        if (rawToken == null || rawToken.isBlank()) {
            throw conflict("Token is missing");
        }
        byte[] bytes;
        try {
            bytes = Base64.getUrlDecoder().decode(rawToken);
        } catch (IllegalArgumentException exception) {
            throw conflict("Token is malformed");
        }
        ShareLink link = repository.findByTokenHash(Hashes.sha256(bytes))
                .orElseThrow(() -> conflict("Token is unknown"));
        if (link.revoked()) {
            throw conflict("Token was revoked");
        }
        if (link.expired(now)) {
            throw conflict("Token is expired");
        }
        return link;
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private static Long numericUserKey(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record IssuedShareLink(String externalId, String rawToken, Instant expiresAt) {
    }

    public record SignedMediaAccess(long treeKey, String mediaExternalId, Instant expiresAt) {
    }
}
