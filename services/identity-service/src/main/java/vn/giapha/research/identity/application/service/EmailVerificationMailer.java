package vn.giapha.research.identity.application.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import vn.giapha.research.identity.infrastructure.outbox.OutboxRelay;
import java.util.Map;
import vn.giapha.research.identity.domain.model.User;

/**
 * Outbox-backed transactional email dispatcher (Task 19.2). Verifications and
 * password resets are queued into {@code outbox_events} inside the same
 * transaction as the user-state mutation; the durable worker drains the queue
 * to the configured provider. A delivery failure never breaks the
 * authentication transaction — the row stays {@code PENDING} until a manual
 * replay succeeds (Task 12.4, Req 13.8).
 */
@Service
public class EmailVerificationMailer {

    private final OutboxRelay outbox;

    public EmailVerificationMailer(OutboxRelay outbox) {
        this.outbox = outbox;
    }

    public void enqueueVerification(User user, String rawToken, Instant expiresAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("to", user.email());
        payload.put("name", user.name());
        payload.put("token", rawToken);
        payload.put("expiresAt", expiresAt.toString());
        payload.put("purpose", "EMAIL_VERIFICATION");
        outbox.append("USER", user.externalId(), null, "EMAIL_VERIFICATION_REQUESTED");
    }
}
