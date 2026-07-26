package vn.giapha.research.identity.domain.model;

import java.time.Instant;

/**
 * Link row from {@code oauth_accounts} (Task 17.1). Uniqueness of
 * {@code (provider, provider_account_id)} is owned by the database
 * ({@code uk_oauth_accounts_provider_account}) so concurrent link attempts are
 * constraint-safe (Task 17.4).
 */
public record OAuthAccountLink(
        long oauthAccountKey,
        long userKey,
        AuthProvider provider,
        String providerAccountId,
        Instant createdAt) {}
