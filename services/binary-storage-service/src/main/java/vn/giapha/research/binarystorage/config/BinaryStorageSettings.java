package vn.giapha.research.binarystorage.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tuning for the binary-storage module, provided as a bean by
 * {@code app-bootstrap} from the typed {@code giapha.blob-gateway} properties.
 * The module deliberately owns no {@code @ConfigurationProperties} class so
 * configuration binding stays centralized in the bootstrap (Task 7.1).
 */
@ConfigurationProperties("giapha.binary-storage")
public record BinaryStorageSettings(
        /** Base URL of the blob control gateway deployment (no trailing slash). */
        String gatewayBaseUrl,
        /** HMAC secrets for gateway request signing; index 0 signs, all verify. */
        List<String> secrets,
        /**
         * Keys accepted on inbound gateway → Spring notifications (upload
         * completion); may be empty until uploads are enabled — verification
         * then fails closed.
         */
        List<String> notifySecrets,
        /** Issuer identity presented to the gateway (GATEWAY_EXPECTED_ISSUERS). */
        String issuer,
        /** Audience bound into every signature (GATEWAY_AUDIENCE). */
        String audience,
        Duration connectTimeout,
        Duration readTimeout,
        /** TTL of capabilities issued to browsers (uploads/downloads). */
        Duration browserSignedUrlTtl,
        /** TTL of capabilities the backend consumes itself, immediately. */
        Duration internalSignedUrlTtl,
        /** Consecutive failures before the breaker opens. */
        int breakerFailureThreshold,
        /** How long an open breaker rejects calls before a half-open probe. */
        Duration breakerOpenDuration) {

    public BinaryStorageSettings {
        if (gatewayBaseUrl == null || gatewayBaseUrl.isBlank()) {
            throw new IllegalArgumentException("gatewayBaseUrl must be configured");
        }
        gatewayBaseUrl = gatewayBaseUrl.replaceAll("/+$", "");
        if (secrets == null || secrets.isEmpty()) {
            throw new IllegalArgumentException("At least one gateway secret is required");
        }
        secrets = List.copyOf(secrets);
        notifySecrets = notifySecrets == null ? List.of() : List.copyOf(notifySecrets);
        if (breakerFailureThreshold < 1) {
            throw new IllegalArgumentException("breakerFailureThreshold must be >= 1");
        }
    }

    /** The active signing key (index 0); older entries remain valid for verification. */
    public String signingSecret() {
        return secrets.getFirst();
    }
}
