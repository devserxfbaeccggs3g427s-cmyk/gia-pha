package vn.giapha.research.identity.config;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import vn.giapha.research.identity.infrastructure.config.ResearchProperties;
import vn.giapha.research.identity.application.bridge.BridgeEs256;
import vn.giapha.research.identity.application.bridge.BridgeKeyRegistry;
import vn.giapha.research.identity.application.bridge.BridgeReplayStore;
import vn.giapha.research.identity.application.bridge.BridgeTokenService;
import vn.giapha.research.identity.domain.bridge.BridgeVerificationKey;

/**
 * Wires the NextAuth bridge (Task 18, ADR-009) from typed properties
 * (Task 7.1) into the application service.
 *
 * <p>The active signing key is read from
 * {@code giapha.auth.bridge-private-keys} (map of {@code kid -> base64url(d, x, y)}
 * coordinates, multiple keys allowed, index 0 signs). The verification set is
 * read from {@code giapha.auth.bridge-public-keys} (same shape, mandatory
 * overlap with the active key). When the active key's JWK triple is omitted
 * or invalid the bean factory fails fast — there is no in-memory dev-mode
 * fallback in production. A separate {@code bridge-dev} profile installs a
 * self-signed key pair so local smoke tests can mint and verify tokens
 * without external secrets.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ResearchProperties.class)
public class BridgeTokenConfig {

    private static final Logger log = LoggerFactory.getLogger(BridgeTokenConfig.class);

    /**
     * Additional configuration beyond {@link ResearchProperties.Auth}: the active
     * signing key is a private coordinate set (kept out of the global properties
     * to avoid leaking it through accidental Actuator exposure).
     */
    @Bean
    @Profile("!bridge-dev")
    BridgeTokenService bridgeTokenServiceProd(BridgeKeyRegistry registry,
            BridgeReplayStore replayStore,
            ResearchProperties properties) {
        Map<String, String> privateKeys = readPrivateKeyMap();
        Map<String, String> publicKeys = readPublicKeyMap();
        if (privateKeys.isEmpty()) {
            throw new IllegalStateException(
                    "Bridge signing keys are required (env GIAPHA_AUTH_BRIDGE_PRIVATE_KEYS_<KID>)");
        }
        if (publicKeys.isEmpty()) {
            throw new IllegalStateException(
                    "Bridge public keys are required (env GIAPHA_AUTH_BRIDGE_PUBLIC_KEYS_<KID>)");
        }
        String activeKeyId = firstKeyId(privateKeys);
        KeyCoordinates coordinates = KeyCoordinates.parse(privateKeys.get(activeKeyId));
        KeyPair activePair = coordinates.toKeyPair();
        BridgeVerificationKey activeKey = new BridgeVerificationKey(activeKeyId,
                activePair.getPublic(), 0);
        registry.install(activeKeyId, activePair.getPrivate(), merge(activeKey, publicKeys));
        log.info("Bridge keys installed: active={} verificationKeys={}",
                activeKeyId, registry.verificationKeys().size());
        BridgeTokenService service = new BridgeTokenService(registry, replayStore,
                properties.auth().bridgeIssuer(), properties.auth().bridgeAudience());
        service.overrideMaxLifetime(BridgeTokenService.MAX_LIFETIME);
        service.overrideActiveKey(activeKeyId);
        return service;
    }

    /**
     * Self-signed bridge for local development and integration tests
     * (Task 18.1). The {@code bridge-dev} profile installs a deterministic
     * key pair so the same token shape can be exercised end-to-end without
     * external secrets.
     */
    @Bean
    @Profile("bridge-dev")
    BridgeTokenService bridgeTokenServiceDev(BridgeKeyRegistry registry,
            BridgeReplayStore replayStore,
            ResearchProperties properties,
            ObjectProvider<BridgeKeyRegistry> registryOverride) {
        registry.installSelfSigned(List.of("dev-active", "dev-previous"));
        BridgeTokenService service = new BridgeTokenService(registry, replayStore,
                properties.auth().bridgeIssuer(), properties.auth().bridgeAudience());
        service.overrideMaxLifetime(BridgeTokenService.MAX_LIFETIME);
        return service;
    }

    @Bean
    BridgeKeyRegistry bridgeKeyRegistry() {
        return new BridgeKeyRegistry();
    }

    @Bean
    BridgeReplayStore bridgeReplayStore() {
        return new BridgeReplayStore(Duration.ofMinutes(10));
    }

    /* --- helpers ------------------------------------------------------- */

    private static Map<String, String> readPrivateKeyMap() {
        return readBridgeKeyMap("GIAPHA_AUTH_BRIDGE_PRIVATE_KEYS");
    }

    private static Map<String, String> readPublicKeyMap() {
        return readBridgeKeyMap("GIAPHA_AUTH_BRIDGE_PUBLIC_KEYS");
    }

    /**
     * Reads {@code GIAPHA_AUTH_BRIDGE_*_KEYS_<KID>} from the process environment.
     * Each value is a JSON object containing {@code x, y} (public) or
     * {@code d, x, y} (private) base64url coordinates.
     */
    private static Map<String, String> readBridgeKeyMap(String prefix) {
        Map<String, String> values = new java.util.TreeMap<>();
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(prefix + "_")) {
                String kid = key.substring(prefix.length() + 1);
                values.put(kid, entry.getValue());
            }
        }
        return values;
    }

    private static String firstKeyId(Map<String, String> map) {
        return map.keySet().iterator().next();
    }

    private static List<BridgeVerificationKey> merge(BridgeVerificationKey activeKey,
            Map<String, String> publicKeys) {
        List<BridgeVerificationKey> keys = new ArrayList<>();
        keys.add(activeKey);
        for (Map.Entry<String, String> entry : publicKeys.entrySet()) {
            if (entry.getKey().equals(activeKey.keyId())) {
                continue;
            }
            PublicKey publicKey = KeyCoordinates.parsePublic(entry.getValue()).publicKey();
            keys.add(new BridgeVerificationKey(entry.getKey(), publicKey, 0));
        }
        return keys;
    }

    /**
     * Holder of {@code (d, x, y)} coordinates used by the bridge codec. A
     * private key entry is always a JSON object with three base64url fields;
     * a public key entry has only {@code x} and {@code y}.
     */
    static final class KeyCoordinates {

        final String d;
        final String x;
        final String y;

        private KeyCoordinates(String d, String x, String y) {
            this.d = d;
            this.x = x;
            this.y = y;
        }

        static KeyCoordinates parse(String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Missing bridge key coordinates");
            }
            String trimmed = value.trim();
            if (!trimmed.startsWith("{")) {
                throw new IllegalArgumentException("Bridge key must be a JSON object");
            }
            String body = trimmed.substring(1, trimmed.length() - 1);
            String d = null;
            String x = null;
            String y = null;
            for (String part : body.split(",")) {
                String[] kv = part.split(":", 2);
                if (kv.length != 2) {
                    continue;
                }
                String key = kv[0].trim().replace("\"", "");
                String v = kv[1].trim().replace("\"", "");
                switch (key) {
                    case "d" -> d = v;
                    case "x" -> x = v;
                    case "y" -> y = v;
                    default -> {
                        /* ignore */
                    }
                }
            }
            if (x == null || y == null) {
                throw new IllegalArgumentException("Bridge key requires x and y");
            }
            return new KeyCoordinates(d, x, y);
        }

        static KeyCoordinates parsePublic(String value) {
            KeyCoordinates coords = parse(value);
            return new KeyCoordinates(null, coords.x, coords.y);
        }

        java.security.KeyPair toKeyPair() {
            if (d == null) {
                throw new IllegalArgumentException("Private key requires d coordinate");
            }
            return BridgeEs256.privateKeyFromCoordinates(d, x, y);
        }

        PublicKey publicKey() {
            return BridgeEs256.publicKeyFromCoordinates(x, y);
        }

        PrivateKey privateKey() {
            return toKeyPair().getPrivate();
        }
    }
}
