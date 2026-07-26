package vn.giapha.research.identity.application.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.giapha.research.identity.infrastructure.config.ResearchProperties;
import vn.giapha.research.identity.application.port.out.VerificationTokenRepository;
import vn.giapha.research.identity.domain.auth.RegistrationInput;
import vn.giapha.research.identity.domain.auth.RegistrationPolicy;
import vn.giapha.research.identity.domain.model.AuthProvider;
import vn.giapha.research.identity.domain.model.NewUser;
import vn.giapha.research.identity.domain.model.User;
import vn.giapha.research.identity.infrastructure.kernel.error.ConflictException;

/**
 * Credential-user registration (Task 19.1, Req 2.2). Validation, hashing,
 * insertion and email-verification-token issuance happen in one transaction
 * so a partial identity row cannot exist on rollback. The legacy
 * {@code EMAIL_ALREADY_EXISTS} contract is preserved verbatim: it is the only
 * domain conflict registration surfaces.
 */
@Service
public class RegistrationService {

    public static final Duration EMAIL_VERIFICATION_TTL = Duration.ofHours(24);

    private final PasswordHasher hasher;
    private final IdentityWriter writer;
    private final VerificationTokenRepository verificationTokens;
    private final EmailVerificationMailer mailer;
    private final boolean emailVerificationRequired;

    public RegistrationService(PasswordHasher hasher, IdentityWriter writer,
            VerificationTokenRepository verificationTokens, EmailVerificationMailer mailer,
            ResearchProperties properties) {
        this.hasher = hasher;
        this.writer = writer;
        this.verificationTokens = verificationTokens;
        this.mailer = mailer;
        this.emailVerificationRequired = properties.auth().requireEmailVerification();
    }

    @Transactional
    public Registered register(RegistrationInput input, Instant now) {
        RegistrationPolicy.validate(input);
        String email = NewUser.normalizeEmail(input.email());
        if (writer.findByEmail(email).isPresent()) {
            throw new ConflictException("EMAIL_ALREADY_EXISTS",
                    "An account with this email already exists");
        }
        String passwordHash = hasher.hash(input.password());
        NewUser newUser = new NewUser(
                writer.newExternalId(),
                email,
                input.name().trim(),
                passwordHash,
                null,
                AuthProvider.CREDENTIALS,
                emailVerificationRequired ? null : now,
                0,
                null,
                now,
                now);
        User user = writer.insertCredentialUser(newUser);

        if (emailVerificationRequired) {
            byte[] token = randomTokenBytes();
            byte[] hash = vn.giapha.research.identity.infrastructure.kernel.crypto.Hashes.sha256(token);
            verificationTokens.insert(user.userKey(),
                    vn.giapha.research.identity.domain.model.VerificationToken.PURPOSE_EMAIL_VERIFICATION,
                    hash, now.plus(EMAIL_VERIFICATION_TTL), now);
            String rawToken = HexFormat.of().formatHex(token);
            mailer.enqueueVerification(user, rawToken, now.plus(EMAIL_VERIFICATION_TTL));
        }
        return new Registered(user, emailVerificationRequired);
    }

    private static byte[] randomTokenBytes() {
        byte[] token = new byte[32];
        new SecureRandom().nextBytes(token);
        return token;
    }

    public record Registered(User user, boolean emailVerificationRequired) {}
}
