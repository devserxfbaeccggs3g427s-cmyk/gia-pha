package com.familya.identity.application.usecase;

import com.familya.identity.application.port.in.RevokeSessionCommand;
import com.familya.identity.application.port.out.IdentityRepository;
import com.familya.identity.domain.model.Session;
import com.familya.platform.error.ForbiddenException;
import com.familya.platform.error.NotFoundException;
import com.familya.platform.telemetry.PlatformMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RevokeSessionUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(RevokeSessionUseCase.class);

    private final IdentityRepository repo;
    private final PlatformMetrics metrics;

    public RevokeSessionUseCase(IdentityRepository repo, PlatformMetrics metrics) {
        this.repo = repo;
        this.metrics = metrics;
    }

    @Transactional
    public void execute(RevokeSessionCommand cmd) {
        metrics.mutationAcceptedCounter("identity-service", "revokeSession").increment();
        Session s = repo.findSession(cmd.sessionId())
                .orElseThrow(() -> new NotFoundException("session " + cmd.sessionId()));
        if (!s.userId().equals(cmd.actingUserId())) {
            throw new ForbiddenException("Session does not belong to acting user.");
        }
        repo.updateSession(s.revoke());
        LOG.info("Revoked session id={} userId={}", s.id(), s.userId());
    }
}
