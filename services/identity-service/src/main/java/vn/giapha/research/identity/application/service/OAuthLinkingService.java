package vn.giapha.research.identity.application.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.giapha.research.identity.infrastructure.outbox.OutboxRelay;
import java.util.Map;
import vn.giapha.research.identity.application.port.out.OAuthAccountRepository;
import vn.giapha.research.identity.application.port.out.UserRepository;
import vn.giapha.research.identity.domain.auth.AuthSession;
import vn.giapha.research.identity.domain.model.AuthProvider;
import vn.giapha.research.identity.domain.model.OAuthAccountLink;
import vn.giapha.research.identity.domain.model.User;
import vn.giapha.research.identity.infrastructure.kernel.error.ConflictException;
import vn.giapha.research.identity.infrastructure.kernel.error.NotFoundException;

/**
 * Google/Facebook account linkage (Task 19.4). After identity cutover the
 * OAuth provider callback lands here instead of writing to the legacy Blob
 * adapter; the {@code oauth_accounts} unique constraint continues to
 * decide linkage races.
 *
 * <p>Successful linkage enqueues an outbox event recording the change so the
 * audit trail and notification fan-out can react; the row insert and the
 * outbox append commit in a single transaction.
 */
@Service
public class OAuthLinkingService {

    private final UserRepository users;
    private final OAuthAccountRepository oauthAccounts;
    private final IdentityWriter identityWriter;
    private final AuthSessionService sessions;
    private final OutboxRelay outbox;

    public OAuthLinkingService(UserRepository users, OAuthAccountRepository oauthAccounts,
            IdentityWriter identityWriter, AuthSessionService sessions,
            OutboxRelay outbox) {
        this.users = users;
        this.oauthAccounts = oauthAccounts;
        this.identityWriter = identityWriter;
        this.sessions = sessions;
        this.outbox = outbox;
    }

    /**
     * Sign in (or link, when the same provider account id is already known)
     * with the supplied OAuth identity. Returns the resolved user plus an
     * opaque Spring session ready for cookie issuance.
     */
    @Transactional
    public LinkedSignIn linkOrSignIn(AuthProvider provider, String providerAccountId,
            String email, String name, String imageUrl, Instant now) {
        if (!provider.isOauth()) {
            throw new IllegalArgumentException("Provider must be Google or Facebook");
        }
        if (providerAccountId == null || providerAccountId.isBlank()) {
            throw new IllegalArgumentException("providerAccountId is required");
        }
        Map<String, Object> outboxPayload = new LinkedHashMap<>();
        outboxPayload.put("provider", provider.dbValue());
        outboxPayload.put("providerAccountId", providerAccountId);
        User user = ensureLinkedUser(provider, providerAccountId, email, name, imageUrl, now,
                outboxPayload);
        AuthSessionService.IssuedSession issued = sessions.issue(user, Set.of(), now);
        return new LinkedSignIn(user, issued, wasLinked(provider, providerAccountId, user));
    }

    private User ensureLinkedUser(AuthProvider provider, String providerAccountId,
            String email, String name, String imageUrl, Instant now,
            Map<String, Object> outboxPayload) {
        Optional<OAuthAccountLink> existingLink =
                oauthAccounts.findByProviderAccount(provider, providerAccountId);
        if (existingLink.isPresent()) {
            User user = users.findByUserKey(existingLink.get().userKey())
                    .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND",
                            "Linked user does not exist"));
            outboxPayload.put("action", "SIGN_IN");
            outboxPayload.put("userExternalId", user.externalId());
            outbox.append("USER", user.externalId(), null, "OAUTH_SIGN_IN");
            return user;
        }
        // No existing link — find or create the user.
        String normalizedEmail = email == null ? null
                : vn.giapha.research.identity.domain.model.NewUser.normalizeEmail(email);
        Optional<User> maybeUser = normalizedEmail == null
                ? Optional.empty() : users.findByEmail(normalizedEmail);
        User user;
        if (maybeUser.isPresent()) {
            user = maybeUser.get();
        } else {
            user = createOAuthUser(provider, email, name, imageUrl, now);
        }
        try {
            oauthAccounts.link(user.userKey(), provider, providerAccountId, now);
        } catch (DuplicateKeyException race) {
            // Lost the race against another concurrent linkage: re-resolve.
            OAuthAccountLink link = oauthAccounts.findByProviderAccount(provider, providerAccountId)
                    .orElseThrow(() -> new ConflictException("OAUTH_RACE_LOST",
                            "OAuth provider linkage race could not be resolved"));
            user = users.findByUserKey(link.userKey())
                    .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND",
                            "Linked user does not exist"));
        }
        outboxPayload.put("action", "LINKED");
        outboxPayload.put("userExternalId", user.externalId());
        outbox.append("USER", user.externalId(), null, "OAUTH_LINKED");
        return user;
    }

    private User createOAuthUser(AuthProvider provider, String email, String name,
            String imageUrl, Instant now) {
        if (email == null || email.isBlank()) {
            throw new ValidationExceptionValue("EMAIL_REQUIRED",
                    "OAuth registration requires a verified email");
        }
        String externalId = identityWriter.newExternalId();
        vn.giapha.research.identity.domain.model.NewUser newUser =
                new vn.giapha.research.identity.domain.model.NewUser(
                        externalId,
                        email,
                        name == null ? email : name,
                        null,
                        imageUrl,
                        provider,
                        now,
                        0,
                        null,
                        now,
                        now);
        return identityWriter.insertCredentialUser(newUser);
    }

    private boolean wasLinked(AuthProvider provider, String providerAccountId, User user) {
        return oauthAccounts.findByProviderAccount(provider, providerAccountId)
                .map(link -> link.userKey() == user.userKey())
                .orElse(false);
    }

    public record LinkedSignIn(User user, AuthSessionService.IssuedSession session, boolean reused) {}

    /** Local exception so the package does not need a top-level import. */
    private static final class ValidationExceptionValue extends RuntimeException {
        ValidationExceptionValue(String code, String message) {
            super(message);
        }
    }
}
