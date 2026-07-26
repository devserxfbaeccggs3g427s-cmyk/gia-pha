package vn.giapha.research.identity.application.service;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.giapha.research.identity.application.port.out.UserRepository;
import vn.giapha.research.identity.domain.model.NewUser;
import vn.giapha.research.identity.domain.model.User;
import vn.giapha.research.identity.infrastructure.kernel.id.Ids;

/**
 * MySQL-backed {@link IdentityWriter}. Centralizes the small bit of glue that
 * registration, OAuth-linking and the migration runner need so each path does
 * not duplicate the {@code findByEmail → insert → return row} dance.
 */
@Service
class MySqlIdentityWriter implements IdentityWriter {

    private final UserRepository users;

    MySqlIdentityWriter(UserRepository users) {
        this.users = users;
    }

    @Override
    public String newExternalId() {
        return Ids.newId();
    }

    @Override
    @Transactional
    public User insertCredentialUser(NewUser newUser) {
        long key = users.insert(newUser);
        return users.findByExternalId(newUser.externalId())
                .orElseThrow(() -> new IllegalStateException(
                        "Inserted user not found by externalId: " + newUser.externalId()));
    }

    @Override
    public Optional<User> findByEmail(String normalizedEmail) {
        return users.findByEmail(normalizedEmail);
    }
}
