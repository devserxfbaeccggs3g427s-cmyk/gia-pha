package vn.giapha.research.identity.application.erasure;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.giapha.research.identity.application.port.out.AuthSessionRepository;
import vn.giapha.research.identity.application.port.out.OAuthAccountRepository;
import vn.giapha.research.identity.application.port.out.UserRepository;
import vn.giapha.research.identity.application.port.out.VerificationTokenRepository;
import vn.giapha.research.identity.domain.model.User;
import vn.giapha.research.identity.infrastructure.kernel.audit.AuditEvents;

/**
 * Erasure planner default (Task 43A). Returns counts and aggregate
 * descriptions without touching any row. The {@link #preview} method is
 * the only public surface; the {@link AuditEvents} constant is referenced
 * so the erasure worker logs a stable event type on execution.
 */
@Service
public class DefaultErasurePlanner implements ErasurePlanner {

    private final UserRepository users;
    private final OAuthAccountRepository oauthAccounts;
    private final VerificationTokenRepository verificationTokens;
    private final AuthSessionRepository sessions;

    public DefaultErasurePlanner(UserRepository users, OAuthAccountRepository oauthAccounts,
            VerificationTokenRepository verificationTokens, AuthSessionRepository sessions) {
        this.users = users;
        this.oauthAccounts = oauthAccounts;
        this.verificationTokens = verificationTokens;
        this.sessions = sessions;
    }

    @Transactional(readOnly = true)
    @Override
    public Preview preview(String externalUserId) {
        User user = users.findByExternalId(externalUserId).orElse(null);
        if (user == null) {
            return new Preview(externalUserId, List.of(), List.of(), List.of(),
                    List.of(new LegalBlocker("USER", "USER_NOT_FOUND")));
        }
        long userKey = user.userKey();
        List<AffectedRow> affected = new ArrayList<>();
        affected.add(new AffectedRow("users", "SOFT_DELETE", 1));
        affected.add(new AffectedRow("oauth_accounts", "DETACH", oauthAccounts.listByUser(userKey).size()));
        affected.add(new AffectedRow("verification_tokens", "DELETE",
                verificationTokens.count()));
        affected.add(new AffectedRow("auth_sessions", "REVOKE",
                sessions.count()));
        return new Preview(externalUserId, affected, List.of(), List.of(), List.of());
    }
}
