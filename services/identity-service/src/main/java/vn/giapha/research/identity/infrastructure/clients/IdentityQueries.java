package vn.giapha.research.identity.infrastructure.clients;

public interface IdentityQueries {
    boolean isKnownUser(String userKey);
    String resolveSession(String sessionId);
    IdentityUser findUser(String externalId);

    record IdentityUser(long userKey, String externalId) {}
}
