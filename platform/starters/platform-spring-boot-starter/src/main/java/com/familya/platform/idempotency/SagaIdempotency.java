package com.familya.platform.idempotency;

import com.familya.platform.api.AsyncOperation;
import com.familya.platform.error.IdempotencyConflictException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Helpers for the idempotent Saga initiation pattern required by Task 13.1.
 *
 * <p>The store is scoped by {@code (service_name, idempotency_key)}; the
 * canonical payload hash is computed from the fields that affect the
 * business outcome (command type, principal, scope, and the Saga inputs).
 * Calling {@link IdempotencyStore#find} first and only writing when absent
 * gives the reserve-or-replay semantic; calling
 * {@link IdempotencyStore#record} afterwards stores the envelope and
 * performs the 409 conflict check on the hash.</p>
 */
public final class SagaIdempotency {

    private SagaIdempotency() { }

    public static String canonicalHash(String commandType, UUID principalId, String scope, String bodyJson) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String body = (commandType == null ? "" : commandType) + "|"
                    + (principalId == null ? "" : principalId.toString()) + "|"
                    + (scope == null ? "" : scope) + "|"
                    + (bodyJson == null ? "" : bodyJson);
            byte[] hash = md.digest(body.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Reserve-or-replay. If the key already exists, return the stored
     * envelope. Otherwise, return {@link Optional#empty()} and let the caller
     * perform the side effects, then call {@link #commit} to record the
     * envelope.
     */
    public static Optional<AsyncOperation> reserve(IdempotencyStore store, String key) {
        if (key == null || key.isBlank()) return Optional.empty();
        return store.find(key);
    }

    public static void commit(IdempotencyStore store, String key, String payloadHash, AsyncOperation envelope) {
        if (key == null || key.isBlank()) return;
        try {
            store.record(key, payloadHash, envelope);
        } catch (IdempotencyConflictException e) {
            throw new IdempotencyConflictException(
                    "Idempotency-Key reuse with different payload: " + e.getMessage());
        }
    }
}
