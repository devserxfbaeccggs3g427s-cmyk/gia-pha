package vn.giapha.research.identity.domain.model;

import vn.giapha.research.identity.infrastructure.kernel.error.ValidationException;

/**
 * Authentication provider of a user account (frozen legacy enum, Req 2.1).
 * Database values are the exact lowercase legacy strings enforced by
 * {@code ck_users_provider}.
 */
public enum AuthProvider {
    CREDENTIALS("credentials"),
    GOOGLE("google"),
    FACEBOOK("facebook");

    private final String dbValue;

    AuthProvider(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static AuthProvider fromDbValue(String value) {
        for (AuthProvider provider : values()) {
            if (provider.dbValue.equals(value)) {
                return provider;
            }
        }
        throw new ValidationException("Unknown auth provider: " + value);
    }

    /** OAuth providers eligible for {@code oauth_accounts} rows. */
    public boolean isOauth() {
        return this != CREDENTIALS;
    }
}
