package vn.giapha.research.identity.application.service;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.giapha.research.identity.application.port.out.UserRepository;
import vn.giapha.research.identity.application.port.out.VerificationTokenRepository;
import vn.giapha.research.identity.domain.model.User;
import vn.giapha.research.identity.domain.model.VerificationToken;
import vn.giapha.research.identity.infrastructure.kernel.crypto.Hashes;
import vn.giapha.research.identity.infrastructure.kernel.error.ValidationException;

/**
 * Email-verification redemption (Task 19.2, Req 2.3). The raw token from the
 * user-supplied link is hashed before lookup; consumption is the
 * single-statement compare-and-set on
 * {@link VerificationTokenRepository#consume(byte[], Instant)} so exactly
 * one request can redeem a token. Successful redemption stamps
 * {@code users.email_verified_at} via {@link UserRepository#markEmailVerified}.
 */
@Service
public class EmailVerificationService {

    private final VerificationTokenRepository verificationTokens;
    private final UserRepository users;

    public EmailVerificationService(VerificationTokenRepository verificationTokens,
            UserRepository users) {
        this.verificationTokens = verificationTokens;
        this.users = users;
    }

    @Transactional
    public User redeem(String rawToken, Instant now) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ValidationException("INVALID_TOKEN");
        }
        byte[] tokenHash = Hashes.sha256(rawToken);
        Optional<VerificationToken> consumed = verificationTokens.consume(tokenHash, now);
        if (consumed.isEmpty()) {
            throw new ValidationException("INVALID_TOKEN");
        }
        VerificationToken token = consumed.get();
        User user = users.findByUserKey(token.userKey())
                .orElseThrow(() -> new IllegalStateException(
                        "Verification token references unknown user_key=" + token.userKey()));
        users.markEmailVerified(user.userKey(), now, now);
        return users.findByUserKey(user.userKey()).orElseThrow(() ->
                new IllegalStateException("User disappeared after verification"));
    }
}
