package vn.giapha.research.identity.application.service;

import java.util.Optional;

import vn.giapha.research.identity.domain.model.User;

/**
 * Outbound port for credential-user insertion (Task 19.1). Implemented by
 * the MySQL adapter; consumed by {@link RegistrationService} so the
 * registration pipeline never touches JDBC directly.
 */
public interface IdentityWriter {

    /** Allocate a fresh external id — same shape as the legacy nanoid. */
    String newExternalId();

    /** Insert a credentials user. Throws {@code ConflictException} on duplicate email. */
    User insertCredentialUser(vn.giapha.research.identity.domain.model.NewUser newUser);

    Optional<User> findByEmail(String normalizedEmail);
}
