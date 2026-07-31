package com.familya.identity.application.port.out;

import com.familya.identity.domain.model.EmailVerificationToken;
import com.familya.identity.domain.model.OAuthLink;
import com.familya.identity.domain.model.Session;
import com.familya.identity.domain.model.User;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IdentityRepository {
    Optional<User> findById(UUID id);
    Optional<User> findByNormalizedEmail(String normalizedEmail);
    void insert(User user, List<OAuthLink> oAuthLinks);
    void update(User user);
    void insertSession(Session session);
    Optional<Session> findSession(UUID sessionId);
    void updateSession(Session session);
    void insertOAuthLink(OAuthLink link);
    Optional<EmailVerificationToken> findEmailVerificationToken(String token);
    void consumeEmailVerificationToken(String token, Instant consumedAt);
}
