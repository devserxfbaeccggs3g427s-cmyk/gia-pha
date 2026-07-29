package com.familya.sharing.application.usecase;

import com.familya.sharing.application.port.in.CreateShareLinkCommand;
import com.familya.sharing.application.port.out.ShareAuthorization;
import com.familya.sharing.application.port.out.ShareChangePublisher;
import com.familya.sharing.application.port.out.ShareLinkRepository;
import com.familya.sharing.domain.model.ShareLink;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class CreateShareLinkUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(CreateShareLinkUseCase.class);
    private static final SecureRandom RNG = new SecureRandom();

    private final ShareLinkRepository repo;
    private final ShareAuthorization authz;
    private final ShareChangePublisher publisher;
    private final PlatformMetrics metrics;

    public CreateShareLinkUseCase(ShareLinkRepository repo, ShareAuthorization authz,
                                  ShareChangePublisher publisher, PlatformMetrics metrics) {
        this.repo = repo;
        this.authz = authz;
        this.publisher = publisher;
        this.metrics = metrics;
    }

    @Transactional
    public Result execute(CreateShareLinkCommand cmd) {
        metrics.mutationAccepted("sharing-service", "createShareLink");
        ShareAuthorization.Decision d = authz.authorize(cmd.treeId(), cmd.actingUser(), cmd.expectedTreeRevision());
        if (!d.isAllowed()) {
            throw new ForbiddenException("Cannot create share link: " + d.reason());
        }
        ShareLink.Scope scope = ShareLink.Scope.valueOf(cmd.scope().toUpperCase());
        ShareLink.Role role = cmd.role() == null ? ShareLink.Role.VIEWER : ShareLink.Role.valueOf(cmd.role().toUpperCase());
        String token = generateToken();
        String tokenHash = sha256Hex(token);
        Instant now = Instant.now();
        ShareLink link = new ShareLink(
                UUID.randomUUID(), cmd.treeId(), scope, cmd.targetId(), role,
                tokenHash, cmd.actingUser(), now, cmd.expiresAt(), null, null, 0L, 0L);
        repo.insert(link);
        publisher.shareLinkCreated(link);
        LOG.info("Created share link id={} tree={} scope={} role={}", link.id(), cmd.treeId(), scope, role);
        return new Result(link.id(), token, link.expiresAt());
    }

    public static String generateToken() {
        byte[] bytes = new byte[32];
        RNG.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record Result(UUID shareId, String token, Instant expiresAt) { }
}
