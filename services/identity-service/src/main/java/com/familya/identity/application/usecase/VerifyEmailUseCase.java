package com.familya.identity.application.usecase;

import com.familya.identity.application.port.in.VerifyEmailCommand;
import com.familya.identity.application.port.out.IdentityEventPublisher;
import com.familya.identity.application.port.out.IdentityRepository;
import com.familya.identity.domain.event.IdentityUserVerified;
import com.familya.identity.domain.exception.InvalidVerificationTokenException;
import com.familya.identity.domain.model.EmailVerificationToken;
import com.familya.identity.domain.model.User;
import com.familya.platform.error.NotFoundException;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class VerifyEmailUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(VerifyEmailUseCase.class);

    private final IdentityRepository repo;
    private final IdentityEventPublisher eventPublisher;
    private final PlatformMetrics metrics;
    private final RegisterUserUseCase.Clock clock;

    public VerifyEmailUseCase(IdentityRepository repo,
                              IdentityEventPublisher eventPublisher,
                              PlatformMetrics metrics,
                              RegisterUserUseCase.Clock clock) {
        this.repo = repo;
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public UUID execute(VerifyEmailCommand cmd) {
        metrics.mutationAcceptedCounter("identity-service", "verifyEmail").increment();
        Instant now = clock.now();
        EmailVerificationToken stored = repo.findEmailVerificationToken(cmd.token())
                .orElseThrow(() -> new InvalidVerificationTokenException("no verification token"));
        if (!stored.userId().equals(cmd.userId())) {
            metrics.mutationFailed("identity-service", "verifyEmail", "token.mismatch");
            throw new InvalidVerificationTokenException("token mismatch");
        }
        if (!stored.isUsable(now)) {
            metrics.mutationFailed("identity-service", "verifyEmail", "token.expired");
            throw new InvalidVerificationTokenException("token expired or consumed");
        }
        User user = repo.findById(cmd.userId())
                .orElseThrow(() -> new NotFoundException("user " + cmd.userId()));
        User verified = user.verified(now);
        repo.update(verified);
        repo.consumeEmailVerificationToken(cmd.token(), now);
        eventPublisher.publish(new IdentityUserVerified(verified.id(), now));
        LOG.info("Verified user id={}", verified.id());
        return verified.id();
    }
}