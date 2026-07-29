package com.familya.identity.application.usecase;

import com.familya.identity.application.port.in.RegisterUserCommand;
import com.familya.identity.application.port.out.IdentityEventPublisher;
import com.familya.identity.application.port.out.IdentityRepository;
import com.familya.identity.application.port.out.RegistrationRateLimiter;
import com.familya.identity.domain.event.IdentityUserCreated;
import com.familya.identity.domain.exception.EmailAlreadyExistsException;
import com.familya.identity.domain.exception.RateLimitExceededException;
import com.familya.identity.domain.model.User;
import com.familya.platform.outbox.OutboxWriter;
import com.familya.platform.security.PasswordEncoder;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class RegisterUserUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(RegisterUserUseCase.class);

    private final IdentityRepository repo;
    private final RegistrationRateLimiter rateLimiter;
    private final PasswordEncoder encoder;
    private final OutboxWriter outbox;
    private final IdentityEventPublisher eventPublisher;
    private final PlatformMetrics metrics;
    private final Clock clock;

    public RegisterUserUseCase(IdentityRepository repo,
                               RegistrationRateLimiter rateLimiter,
                               PasswordEncoder encoder,
                               OutboxWriter outbox,
                               IdentityEventPublisher eventPublisher,
                               PlatformMetrics metrics,
                               Clock clock) {
        this.repo = repo;
        this.rateLimiter = rateLimiter;
        this.encoder = encoder;
        this.outbox = outbox;
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public UUID execute(RegisterUserCommand cmd) {
        metrics.mutationAcceptedCounter("identity-service", "registerUser").increment();
        Instant now = clock.now();
        if (!rateLimiter.tryRegister(cmd.ipAddress(), now)) {
            throw new RateLimitExceededException("Registration rate limit exceeded for ip " + cmd.ipAddress());
        }
        String normalized = normalize(cmd.email());
        repo.findByNormalizedEmail(normalized).ifPresent(u -> {
            throw new EmailAlreadyExistsException(normalized);
        });
        String hash = encoder.hash(cmd.password());
        User user = new User(
                UUID.randomUUID(),
                normalized,
                hash,
                cmd.verificationRequired() ? User.VerificationState.PENDING : User.VerificationState.VERIFIED,
                new User.LockoutState(false, null),
                0,
                now,
                0L);
        repo.insert(user, List.of());
        eventPublisher.publish(new IdentityUserCreated(user.id(), normalized, cmd.verificationRequired(), now));
        LOG.info("Registered user id={} verificationRequired={}", user.id(), cmd.verificationRequired());
        return user.id();
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase();
    }

    public interface Clock { Instant now(); }
}
