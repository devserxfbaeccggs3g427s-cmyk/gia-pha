package com.familya.identity.application.usecase;

import com.familya.identity.application.port.in.AuthenticateCommand;
import com.familya.identity.application.port.out.IdentityEventPublisher;
import com.familya.identity.application.port.out.IdentityRepository;
import com.familya.identity.domain.event.IdentityCredentialsRehashed;
import com.familya.identity.domain.event.IdentityUserLocked;
import com.familya.identity.domain.exception.AccountLockedException;
import com.familya.identity.domain.exception.InvalidCredentialsException;
import com.familya.identity.domain.model.Session;
import com.familya.identity.domain.model.User;
import com.familya.platform.security.PasswordEncoder;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuthenticateUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(AuthenticateUseCase.class);

    private final IdentityRepository repo;
    private final PasswordEncoder encoder;
    private final IdentityEventPublisher eventPublisher;
    private final PlatformMetrics metrics;
    private final RegisterUserUseCase.Clock clock;
    private final int failedAttemptsLimit;
    private final Duration lockoutWindow;
    private final Duration sessionIdle;
    private final Duration sessionAbsolute;

    public AuthenticateUseCase(IdentityRepository repo,
                               PasswordEncoder encoder,
                               IdentityEventPublisher eventPublisher,
                               PlatformMetrics metrics,
                               RegisterUserUseCase.Clock clock,
                               @Value("${familya.identity.failed-attempts-limit:5}") int failedAttemptsLimit,
                               @Value("${familya.identity.lockout-window-minutes:15}") long lockoutWindowMinutes,
                               @Value("${familya.identity.session-idle-minutes:30}") long sessionIdleMinutes,
                               @Value("${familya.identity.session-absolute-hours:12}") long sessionAbsoluteHours) {
        this.repo = repo;
        this.encoder = encoder;
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
        this.clock = clock;
        this.failedAttemptsLimit = failedAttemptsLimit;
        this.lockoutWindow = Duration.ofMinutes(lockoutWindowMinutes);
        this.sessionIdle = Duration.ofMinutes(sessionIdleMinutes);
        this.sessionAbsolute = Duration.ofHours(sessionAbsoluteHours);
    }

    @Transactional(noRollbackFor = { InvalidCredentialsException.class, AccountLockedException.class })
    public Session execute(AuthenticateCommand cmd) {
        metrics.mutationAcceptedCounter("identity-service", "authenticate").increment();
        Instant now = clock.now();
        User user = repo.findByNormalizedEmail(normalize(cmd.email()))
                .orElseThrow(() -> {
                    metrics.mutationFailed("identity-service", "authenticate", "credentials.invalid");
                    return new InvalidCredentialsException();
                });
        if (user.lockout().locked() && user.lockout().lockedUntil().isAfter(now)) {
            throw new AccountLockedException("Account is locked until " + user.lockout().lockedUntil());
        }
        if (!encoder.matches(cmd.password(), user.bcryptHash())) {
            int attempts = user.failedAttempts() + 1;
            if (attempts >= failedAttemptsLimit) {
                Instant lockUntil = now.plus(lockoutWindow);
                User updated = user.withLockout(new User.LockoutState(true, lockUntil), attempts, now);
                repo.update(updated);
                eventPublisher.publish(new IdentityUserLocked(updated.id(), lockUntil, now));
                LOG.warn("User locked id={} until={}", updated.id(), lockUntil);
                metrics.mutationFailed("identity-service", "authenticate", "credentials.locked");
                throw new AccountLockedException("Account is locked until " + lockUntil);
            }
            User updated = user.withLockout(user.lockout(), attempts, now);
            repo.update(updated);
            metrics.mutationFailed("identity-service", "authenticate", "credentials.invalid");
            throw new InvalidCredentialsException();
        }
        User rehash = user.withLockout(new User.LockoutState(false, null), 0, now);
        if (encoder.needsRehash(user.bcryptHash())) {
            rehash = rehash.withBcrypt(encoder.hash(cmd.password()), now);
            eventPublisher.publish(new IdentityCredentialsRehashed(rehash.id(), now));
        }
        repo.update(rehash);
        Session session = new Session(
                UUID.randomUUID(),
                rehash.id(),
                now,
                now.plus(sessionAbsolute),
                now.plus(sessionIdle),
                cmd.userAgent(),
                ipHash(cmd.ipAddress()));
        repo.insertSession(session);
        return session;
    }

    private static String normalize(String email) { return email.trim().toLowerCase(); }
    private static String ipHash(String ip) { return ip == null ? null : Integer.toHexString(ip.hashCode()); }
}
