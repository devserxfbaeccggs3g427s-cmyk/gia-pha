package com.familya.platform.idempotency;

import com.familya.platform.api.AsyncOperation;

import java.util.Optional;
import java.util.UUID;

/**
 * Idempotency port. The Gateway records (key, payload-hash, operation)
 * and replays the recorded response on retry. Each service that issues
 * a cross-service mutation implements this port against its own
 * idempotency store (MySQL is the baseline; Redis is permitted only
 * when the persistence guarantee is documented and approved).
 */
public interface IdempotencyStore {

    /**
     * Returns the recorded operation for the given key, if any.
     */
    Optional<AsyncOperation> find(String key);

    /**
     * Records the operation under the given key with the given payload
     * hash. If a different payload hash already exists for the key,
     * the implementation MUST throw {@link com.familya.platform.error.IdempotencyConflictException}.
     */
    void record(String key, String payloadHash, AsyncOperation operation);
}
