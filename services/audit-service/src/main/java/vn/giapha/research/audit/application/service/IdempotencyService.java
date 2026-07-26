package vn.giapha.research.audit.application.service;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import vn.giapha.research.audit.application.port.out.IdempotencyRepository;
import vn.giapha.research.audit.domain.model.ProcessedCommand;
import vn.giapha.research.audit.support.ConflictException;
import vn.giapha.research.audit.support.Hashes;
import vn.giapha.research.audit.support.TimeProvider;

/**
 * Idempotency execution guard (Task 11.3, Requirement 13.5-13.6).
 *
 * <p>Behavior:
 * <ul>
 *   <li>first execution for {@code (scope, key)} runs the work and caches the
 *       committed response;</li>
 *   <li>a retry with the same key <em>and same request hash</em> replays the
 *       cached response without mutating again;</li>
 *   <li>the same key with a <em>different</em> request hash is rejected with
 *       HTTP 409;</li>
 *   <li>a retry while the original request is still in flight is rejected
 *       with 409 so no request executes twice concurrently.</li>
 * </ul>
 *
 * <p>The claim is serialized by the {@code uk_processed_commands_scope_key}
 * unique key and must run <em>outside</em> the business transaction: the claim
 * commits first, the work commits second, and a failed work releases the
 * claim so honest retries keep working (at-least-once with idempotent effect).
 */
@Service
public class IdempotencyService {

    /** Legacy PWA offline queue retries within a day; keep cached results 24h. */
    public static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private final IdempotencyRepository repository;
    private final TimeProvider time;

    public IdempotencyService(IdempotencyRepository repository, TimeProvider time) {
        this.repository = repository;
        this.time = time;
    }

    /** Cached (or fresh) HTTP outcome of an idempotent command. */
    public record Outcome(int status, String body, boolean replayed) {
    }

    /** Scope = actor + operation (+ tree tenant) per design.md §Audit and Idempotency. */
    public static String scope(String actorExternalId, String operation, String treeExternalId) {
        return "actor:" + actorExternalId + "|op:" + operation
                + (treeExternalId == null ? "" : "|tree:" + treeExternalId);
    }

    /** Canonical request fingerprint: method, path and raw body, length-prefixed. */
    public static byte[] requestHash(String method, String path, byte[] body) {
        return Hashes.sha256Canonical(method, path,
                body == null ? "" : new String(body, java.nio.charset.StandardCharsets.ISO_8859_1));
    }

    public Outcome execute(String scope, String idempotencyKey, byte[] requestHash,
            Supplier<Outcome> work) {
        return execute(scope, idempotencyKey, requestHash, DEFAULT_TTL, work);
    }

    public Outcome execute(String scope, String idempotencyKey, byte[] requestHash, Duration ttl,
            Supplier<Outcome> work) {
        // Two passes at most: the second handles an expired row released below.
        for (int attempt = 0; attempt < 2; attempt++) {
            Instant now = time.now();
            if (repository.claim(scope, idempotencyKey, requestHash, now.plus(ttl))) {
                return runClaimed(scope, idempotencyKey, work);
            }
            Optional<ProcessedCommand> existing = repository.find(scope, idempotencyKey);
            if (existing.isEmpty()) {
                continue; // Row vanished between claim and find (expiry cleanup); retry once.
            }
            ProcessedCommand command = existing.get();
            if (command.expiredAt(now)) {
                repository.release(scope, idempotencyKey);
                continue;
            }
            if (!MessageDigest.isEqual(command.requestHash(), requestHash)) {
                throw new ConflictException(
                        "Idempotency-Key was already used with a different request",
                        Map.of("reason", "IDEMPOTENCY_KEY_REUSE"));
            }
            if (command.completed()) {
                return new Outcome(command.responseStatus(), command.responseBody(), true);
            }
            throw new ConflictException(
                    "A request with this Idempotency-Key is still being processed",
                    Map.of("reason", "IDEMPOTENCY_IN_PROGRESS"));
        }
        throw new ConflictException("Could not acquire idempotency claim; retry the request");
    }

    private Outcome runClaimed(String scope, String idempotencyKey, Supplier<Outcome> work) {
        Outcome outcome;
        try {
            outcome = work.get();
        } catch (RuntimeException e) {
            // The business transaction rolled back: free the key for honest retries.
            repository.release(scope, idempotencyKey);
            throw e;
        }
        repository.complete(scope, idempotencyKey, outcome.status(), outcome.body(), time.now());
        return new Outcome(outcome.status(), outcome.body(), false);
    }
}
