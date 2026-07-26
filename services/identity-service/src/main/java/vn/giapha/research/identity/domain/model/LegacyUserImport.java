package vn.giapha.research.identity.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * One legacy user from {@code data/users.json} transformed into relational
 * shape (Task 17.3): the {@code users} row plus its {@code oauth_accounts}
 * rows and, when the legacy record still carried a pending email verification,
 * the {@code verification_tokens} row.
 */
public record LegacyUserImport(
        NewUser user,
        List<OAuthLinkImport> oauthAccounts,
        PendingVerification verification) {

    public record OAuthLinkImport(AuthProvider provider, String providerAccountId) {}

    /** Legacy stored the SHA-256 hex of the raw token; carried over verbatim. */
    public record PendingVerification(byte[] tokenHash, Instant expiresAt) {}
}
