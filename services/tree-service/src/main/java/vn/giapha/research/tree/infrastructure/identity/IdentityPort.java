package vn.giapha.research.tree.infrastructure.identity;

import java.util.Optional;

public interface IdentityPort {
    Optional<IdentityUser> findUser(String externalId);

    record IdentityUser(long userKey, String externalId) {}
}
